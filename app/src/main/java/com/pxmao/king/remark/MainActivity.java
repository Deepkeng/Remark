package com.pxmao.king.remark;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static String pathString = "";
    public static String root = "/sdcard/";
    public static String copyDBName = "backups/EnMicroMsg.db";
    public static String copyDBPath = root + copyDBName;
    private static String configName = "config.txt";
    private static final String CONFIG_PATH = root + configName;
    private static final String WECHAT_DB_NAME = "EnMicroMsg.db";
    private static final String WECHAT_DB_PARENT_DIRECTORY = "/data/data/com.tencent.mm/MicroMsg";
    private static final int INTERVAL = 1000;     //数据库复制微信间隔
    public List<DataBean> list;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    }

    @Override
    protected void onStart() {
        super.onStart();
        new Thread() {
            @Override
            public void run() {
                File weiXinMsgDB = obtainDatabaseFile();//复制微信数据库到sd卡
                String password = calculatePsw();//打开数据的密码
                String data = getData();//获取文件json字符串
                parserFile(data);// 解析json并封装
                updateWeiXinDB(weiXinMsgDB, password);//修改微信数据库
            }


        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void parserFile(String data) {
        try {
            list = new ArrayList<DataBean>();
            JSONObject jsonObject = new JSONObject(data);//解析json
            JSONArray data1 = (JSONArray) jsonObject.get("data");
            Log.d(TAG, "解析出来的json: " + data1);

            for (int i = 0; i < data1.length(); i++) {
                DataBean dataBaen = new DataBean();
                JSONObject listdata = (JSONObject) data1.get(i);
                // Log.d(TAG,"listdata:"+listdata);
                String remark = (String) listdata.get("remark");
                String nickname = (String) listdata.get("nickname");
                //封装到bean
                dataBaen.setNickname(nickname);
                dataBaen.setRemark(remark);
                list.add(dataBaen);
                Log.d(TAG, "备注: " + remark + "   昵称: " + nickname);

            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    /**
     * 更改微信数据库好友的备注信息
     *
     * @param databaseFile
     */
    public void updateWeiXinDB(File databaseFile, String password) {

        SQLiteDatabase.loadLibs(this);

        SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
            @Override
            public void preKey(SQLiteDatabase sqLiteDatabase) {
            }

            @Override
            public void postKey(SQLiteDatabase sqLiteDatabase) {
                //database.rawExecSQL("PRAGMA cipher_migrate")这句最为关键，原因如下：
                //现在SQLCipher for Android已经是3.X版本了，而微信居然还停留在2.X时代，所以这句话是为了能够用3.X的开源库兼容2.X的加密解密方法，如果不加这句话，是无法对数据库进行解密的。
                sqLiteDatabase.rawExecSQL("PRAGMA cipher_migrate ; ");
            }
        };

        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null, hook);//打开数据库，获得数据库对象

        //遍历list数据，拿到数据就进行修改
        for (int i = 0; i < list.size(); i++) {
            String nickname = list.get(i).getNickname();
            String remark = list.get(i).getRemark();
            database.execSQL("update rcontact set conRemark = '" + remark + "' where nickname = '" + nickname + "'");//修改语句
            // update rcontact set conRemark ='weixin2' where nickname = 'J.Yong'
        }

        //查询remark表，看修改了没有
        Cursor cursor = database.query("rcontact", null, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String mConRemark = cursor.getString(cursor.getColumnIndex("conRemark"));
            String mNickname = cursor.getString(cursor.getColumnIndex("nickname"));
            Log.d(TAG, " 备注: " + mConRemark + "   昵称: " + mNickname);
        }
        cursor.close();
        database.close();
    }

    //requestApi(jsonStr);


        /*Cursor cursor = database.query("rcontact", new String[]{"nickname"}, "showHead > ? and username <> ? ", new String[]{"33", "weixin"}, null, null, null, null);
        while (cursor.moveToNext()) {
            mNickname = cursor.getString(cursor.getColumnIndex("nickname"));
            Log.d(TAG, "getData: nickname " + mNickname);
        }*/


    //读取文件
    public String getData() {
        //读取txt文件
        InputStream in = null;
        try {
            in = new FileInputStream("/sdcard/backups/remark1.txt");

            byte[] b = new byte[1024];
            int length = 0;
            StringBuffer sb = new StringBuffer();
            while ((length = in.read(b)) != -1) {
                //以前在这出现乱码问题，后来在这设置了编码格式
                sb.append(new String(b, 0, length, "UTF-8"));
            }
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    //获取打开数据密码
    public String calculatePsw() {
        String password = "";

        String imei = obtainIMEICode();

        String uinCode = obtainUinCode();

        String encryptionStr = "";

        if (!TextUtils.isEmpty(imei) && !TextUtils.isEmpty(uinCode)) {
            try {
                encryptionStr = MD5Utils.get32MD5Value(imei + uinCode);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (!TextUtils.isEmpty(encryptionStr)) {
            password = encryptionStr.substring(0, 7);
        }
        Log.d(TAG, "数据库密码：" + password);
        return password;
    }

    public String obtainIMEICode() {
        String imei;
        imei = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        return imei;
    }

    /**
     * 从微信复制 EnMicroMsg.db
     *
     * @return the copy database
     */
    public File obtainDatabaseFile() {
        File copyDB = null;
//        String databasePath = getMicroMsgPath();
        String databasePath = getCurrentDBPath();

        if (!TextUtils.isEmpty(databasePath)) {
//           amend file permission
            if (isDBAmend()) {
                amendFilePermission(databasePath);
                copyDB = copyFileToSDCard(getApplicationContext().getDatabasePath(databasePath));
            } else {
                copyDB = new File(copyDBPath);
            }
        }
        Log.d(TAG, "复制数据库成功");
        return copyDB;
    }


    /**
     * 获取当前登录账户的微信数据库的路径
     *
     * @return wechant database path
     */
    public String getCurrentDBPath() {
        String dbPath = "";
        try {
            amendFilePermission(WECHAT_DB_PARENT_DIRECTORY);

            String uinCode = obtainUinCode();

            String encryptionPath = MD5Utils.get32MD5Value("mm" + uinCode);       //wechat encrypt method :md5(mm + wechat uin code )

            amendFilePermission(WECHAT_DB_PARENT_DIRECTORY + File.separator + encryptionPath);

            dbPath = WECHAT_DB_PARENT_DIRECTORY + File.separator + encryptionPath + File.separator + WECHAT_DB_NAME;

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return dbPath;
    }


    /**
     * 根据传入的文件路径，修改文件权限为可读，可写，可执行
     *
     * @param filePath 文件的路径
     */
    public void amendFilePermission(String filePath) {
        String cmd = " chmod 777 " + filePath;
        executeCMD(cmd);
    }


    /**
     * 执行命令行语句
     *
     * @param command 命令行指令
     */
    public void executeCMD(String command) {
        Process process = null;
        DataOutputStream os = null;

        try {
            String cmd = command;

            process = Runtime.getRuntime().exec("su");

            os = new DataOutputStream(process.getOutputStream());

            os.writeBytes(cmd + " \n");
            os.writeBytes(" exit \n");
            os.flush();

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //获取微信的UIN码
    public String obtainUinCode() {
        String value = null;
        InputStream inputStream = null;
        try {
            String uinFile = "/data/data/com.tencent.mm/shared_prefs/system_config_prefs.xml";

            amendFilePermission(uinFile);

            File file = new File(uinFile);

            inputStream = new FileInputStream(file);
            //获取工厂对象，以及通过DOM工厂对象获取DOMBuilder对象
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            //解析XML输入流，得到Document对象，表示一个XML文档
            Document document = builder.parse(inputStream);
            //获得文档中的次以及节点
            Element element = document.getDocumentElement();
            NodeList personNodes = element.getElementsByTagName("int");
            for (int i = 0; i < personNodes.getLength(); i++) {
                Element personElement = (Element) personNodes.item(i);
                value = personElement.getAttribute("value");
//                System.out.println(value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return value;
    }


    /**
     * 将文件复制到sdcard的根目录
     *
     * @param file weChat source database
     * @return copy database
     */
    public File copyFileToSDCard(File file) {

        amendFilePermission(copyDBPath);
        File copyFile = new File(copyDBPath);
        try {
            copyFile.createNewFile();
            copyFile.setReadable(true);
            copyFile.setWritable(true);
            copyFile.setExecutable(true);

            FileOutputStream outputStream = new FileOutputStream(copyFile);
            FileInputStream inputStream = new FileInputStream(file);

            int len;
            byte b[] = new byte[1024];

            while ((len = inputStream.read(b)) != -1) {
                outputStream.write(b, 0, len);
            }

            outputStream.flush();

            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

//        save configuration
        JSONObject object = new JSONObject();
        {
            try {
                object.put("amendTime", file.lastModified());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        saveConfig(object.toString());

        return copyFile.exists() ? copyFile : null;
    }


    /**
     * 数据转换为json
     *
     * @param cursor database cursor
     * @return the data
     */
    public String getData(Cursor cursor, SQLiteDatabase database) {

        JSONArray arr = new JSONArray();
        String iconUrl = "";
        try {
            while (cursor.moveToNext()) {

//                JSONObject object = new JSONObject();
//                String username = cursor.getString(cursor.getColumnIndex("username"));

//                database.query("img_flag", null, "username = ?", new String[]{username}, null, null, null, "0,1");

//                Cursor cursor2 = database.query("img_flag", null, "username = ?", new String[]{username}, null, null, null, "0,1");
//                while (cursor2.moveToNext()) {
//                    iconUrl = cursor2.getString(cursor2.getColumnIndex("reserved1"));
//                    Log.d(TAG, "getData: icon url " + iconUrl);
//                }
//                cursor2.close();

//                object.put("icon", iconUrl);
//                object.put("account", username);
//                object.put("phone", "");
//                object.put("qq", "");
//                object.put("sex", "");
//                object.put("nickname", cursor.getString(cursor.getColumnIndex("nickname")));
//                object.put("age", "");
//                arr.put(object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getData: arr str : " + arr.toString());
        return arr.toString();
    }


    /**
     * 判断被修改的文件
     * <p>
     * if the database amend time greater than config's amend time about 10 min , reset the config's amend time and copy the database
     */
    private boolean isDBAmend() {
        boolean flag = true;

        Config config = parseConfig(getConfig());

        File wxDB = new File(pathString);

        if (config != null && wxDB.exists()) {
            long time = wxDB.lastModified();
            if ((time - config.getAmendTime()) > INTERVAL) {
                flag = true;
            } else {
                flag = false;
            }
        }
        return flag;
    }


    /**
     * 解析配置文件
     *
     * @param json come from getConfig() return ;
     * @return config object
     */
    private Config parseConfig(String json) {
        Config config = new Config();
        try {
            JSONObject object = new JSONObject(json);
            config.setAmendTime(object.getLong("amendTime"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return config;
    }


    /**
     * 保存应用程序配置
     *
     * @param config config file
     */
    private void saveConfig(String config) {
        File configFile = new File(CONFIG_PATH);
        FileOutputStream fos = null;
        try {
            if (!configFile.exists()) {
                configFile.createNewFile();
            }
            fos = new FileOutputStream(configFile);
            fos.write(config.getBytes(), 0, config.getBytes().length);
            fos.flush();
            Log.d(TAG, "saveConfig: save config  success ");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 获取应用程序配置文件
     *
     * @return config file message
     */
    private String getConfig() {
        String jsonStr = "";
        FileInputStream fis = null;
        File config = new File(CONFIG_PATH);
        try {
            if (config.exists()) {
                byte[] data = new byte[1024];
                int len;
                fis = new FileInputStream(config);
                while ((len = fis.read(data, 0, data.length)) != -1) {
                    jsonStr = new String(data, 0, len, "UTF-8");
                    Log.d(TAG, "getConfig: jsonStr " + jsonStr);
                }
                Log.d(TAG, "getConfig:  get config success");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return jsonStr;
    }

}
