package com.huanghao.suanier.groupcam44b;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Policy;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeoutException;

public class MainActivity extends Activity {
    private Camera mCamera;
    private CameraPreview mPreview;
    static String  TAG ="GroupCam";
    Button btn_Connect;
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private Handler receiveHandler=new Handler();
    private double focusDistance = 1;
    private Calendar picTime;
    private TextView tv_info;
    private TextView tv_countDown;
    private EditText et_IP;
    private SocketClient client;
    private  EditText et_prefix;
    private long milisInterval=1;
    private CountDownTimer countDownTimer;
    File pictureFile;
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            tv_info.append("\nPhoto Taken");

            pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);

            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            }
            try {
                Log.d(TAG, "File prepared:"+pictureFile.getPath());
                FileOutputStream fos = new FileOutputStream(pictureFile);
                Log.d(TAG, "Saving:"+pictureFile.getPath());
                fos.write(data);
                fos.close();
                Log.d(TAG, "File saved:"+pictureFile.getPath());
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

           new Thread()
           {
               @Override
               public void run() {
                   super.run();
                   Log.d(TAG, "Upload  file: ");
                   client.sendFile(pictureFile, client.ip, 21, "anonymous", "");
               }
           }.start();
            mCamera.stopPreview();
            mCamera.startPreview();
            tv_info.setText("");
        }
    };
    @Override
    protected void onPause() {
        super.onPause();
       if(mCamera!=null)
           mCamera.release();
    }
    //Convert integer to IPv4
    private String intToIp(int i) {

        return (i & 0xFF ) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16 ) & 0xFF) + "." +
                ( i >> 24 & 0xFF) ;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv_info=(TextView)findViewById(R.id.textView_info);
        et_IP=(EditText)findViewById(R.id.editText_IP);
        et_prefix=(EditText)findViewById(R.id.editText_prefix);
        tv_countDown=(TextView)findViewById(R.id.textView_timeLabel);
        //Check Local IP
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        String ip = intToIp(ipAddress);
        et_prefix.setText(ip.split("\\.")[3]);
        tv_info.setText("Local IP:"+ip);
        // Create an instance of Camera
        mCamera = Camera.open();
//        if(mCamera.getParameters().isSmoothZoomSupported()==false)
//            Log.v(TAG, "Zoom not supported" );
//        else
//            Log.v(TAG, "Zoom supported" );
        //mCamera.startSmoothZoom(10);
        Camera.Parameters para =mCamera.getParameters();
        Log.v(TAG, "Camera focus Mode:" + para.getFocusMode());


        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // get an image from the camera
                        mCamera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {

                                if (success)//success表示对焦成功
                                {
                                    Log.v(TAG, "myAutoFocusCallback: success...");
                                    //myCamera.setOneShotPreviewCallback(null);

                                } else {
                                    //未对焦成功
                                    Log.v(TAG, "myAutoFocusCallback: 失败了...");

                                }
                            }
                        });
                        mCamera.takePicture(null, null, mPicture);
                        Log.e(TAG,"Taking Photo");

                    }
                }
        );
        btn_Connect =(Button)findViewById(R.id.button_Connect);
        btn_Connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(et_IP.getWindowToken(), 0);

                String content = et_IP.getText().toString();
                if (content.length() == 0) {
                    Toast.makeText(MainActivity.this, "Input IP and Port .\nEg:127.0.0.1:8080", Toast.LENGTH_SHORT).show();
                    return;
                }
                String checkIp[] = new String[2];//^(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9])\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[0-9])(\:)(6553[0-5]|655[0-2][0-9]|65[0-4][0-9][0-9]|6[0-4]{3}[0-9]|[0-5]{4}[0-9]|(\d{1,4}))$
                String RegMatch = "^(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9])\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\." +
                        "(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]{1}[0-9]{2}|[1-9]{1}[0-9]{1}|[0-9])(\\:)" +
                        "(6553[0-5]|655[0-2][0-9]|65[0-4][0-9][0-9]|6[0-4]{3}[0-9]|[0-5]{4}[0-9]|(\\d{1,4}))$";
                if (content.matches(RegMatch)) {
                    checkIp = content.split("\\:");


                    if (client!=null)
                    {


                            Toast.makeText(MainActivity.this, "Input IP and Port .\nEg:127.0.0.1:8080", Toast.LENGTH_SHORT).show();


                    }
                    client = new SocketClient(checkIp[0], Integer.valueOf(checkIp[1]).intValue());
                    client.start();

                }

            }
        });

    }
    private  File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "GroupCams");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSSS").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    et_prefix.getText()+"_IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    //Check Camera Existence
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public class SocketClient extends Thread {
        public String ip;
        public int port;
        Socket socket;
        String initMsg="Client in";
        SocketClient(String arg_ip, int arg_port) {
            ip = arg_ip;
            port = arg_port;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "1", Toast.LENGTH_SHORT).show();
                }
            });
        }
        void close()
        {

        }
        public String sendFile(final File file,String ftpUrl,int ftpPort,String username,String password)
        {
            FTPClient ftpClient = new FTPClient();
            FileInputStream fis = null;
            String returnMessage = "0";
            if(file.canRead())
            {
                try {
                    ftpClient.setConnectTimeout(10000);
                    ftpClient.connect(ftpUrl, ftpPort);
                    boolean loginResult = ftpClient.login(username, password);
                    int returnCode = ftpClient.getReplyCode();
                    if (loginResult && FTPReply.isPositiveCompletion(returnCode)) {// 如果登录成功

                        // 设置上传目录
                        ftpClient.changeWorkingDirectory("/");
                        ftpClient.setBufferSize(1024);
                        ftpClient.setControlEncoding("UTF-8");
                        ftpClient.enterLocalPassiveMode();
                        ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Begin Uploading: " + file.getName(), Toast.LENGTH_SHORT).show();
                            }
                        });
                        fis = new FileInputStream(file);
                        ftpClient.storeFile(file.getName(), fis);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Uploaded", Toast.LENGTH_SHORT).show();
                            }
                        });
                        returnMessage = "1";   //上传成功
                    } else {// 如果登录失败
                        returnMessage = "0:"+returnCode;
                    }


                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("FTP客户端出错！", e);
                } finally {
                    //IOUtils.closeQuietly(fis);
                    try {
                        ftpClient.disconnect();
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException("关闭FTP连接发生异常！", e);
                    }
                }



            }
            return returnMessage;
        }
        public void connectToServer() {
            try {// 创建一个Socket对象，并指定服务端的IP及端口号

                socket = new Socket(ip, port);
                Log.e(TAG, ip+":"+port);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Host can not be reached", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            if(socket.isConnected() == true)
            {
                try {
                    socket.getOutputStream().write(initMsg.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                receiveThread rt=new receiveThread(socket);
                rt.start();

            }


        }

        @Override
        public void run() {
            //Looper.prepare();
            //Looper.loop();

            this.connectToServer();

        }

    }

    public class receiveThread extends Thread {
        private InputStream inStream = null;
        private byte readBuffer[]=new byte[254];
        //private Handler receiveMsgHandler;
        receiveThread(Socket s) {
            try {
                this.inStream = s.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }
            });
            while(true)
            {
                try {
                    int count=inStream.read(readBuffer,0,254);
                    // Log.e(TAG, "input:"+count);
                    if(count>0)
                    {

                        receiveHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                String str=new String(readBuffer);
                                Log.e(TAG,str);
                                if(str.startsWith("<CFG>")) {
                                    str = str.substring(5);

                                    String strArray[] = str.split("\\;");
                                    picTime= Calendar.getInstance();
                                    for (int i = 0; i < strArray.length; i++) {
                                        String strTmp[] = strArray[i].split("\\:");
                                        switch (strTmp[0]) {
                                            case "FD":
                                                focusDistance = Integer.valueOf(strTmp[1]);
                                                break;//Focus Distance
                                            case "TIME":
                                                SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
                                                try {
                                                    Date date =sdf.parse(strTmp[1]);

                                                    picTime.setTime(date);
                                                    picTime.set(Calendar.MILLISECOND,0);

                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                    Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
                                                }


                                        }
                                    }
                                    if(picTime==null)
                                    {
                                        //Toast.makeText(MainActivity.this, "Fuck", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                   // Toast.makeText(MainActivity.this, "FD:"+focusDistance+" TIME:"+picTime.get(Calendar.MINUTE) +":"+picTime.get(Calendar.SECOND), Toast.LENGTH_SHORT).show();
                                }
                                else if (str.startsWith("<PIC>")) {
                                    new Thread()
                                    {
                                        @Override
                                        public void run() {
                                            super.run();
                                            {
                                                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                                                    @Override
                                                    public void onAutoFocus(boolean success, Camera camera) {

                                                        if (success)//success表示对焦成功
                                                        {
                                                            Log.v(TAG, "myAutoFocusCallback: success...");
                                                            //myCamera.setOneShotPreviewCallback(null);

                                                        } else {
                                                            //未对焦成功
                                                            Log.v(TAG, "myAutoFocusCallback: 失败了...");

                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }.start();
                                    long time =picTime.getTimeInMillis()-(Calendar.getInstance().getTimeInMillis()+milisInterval);
                                    Log.d(TAG, time + "");
                                    //tv_info.append("\nActual Pic Time:" + time);
                                    //tv_info.append("\nTime interval with PC:" + milisInterval);
                                    countDownTimer=new CountDownTimer(time,10) {
                                        @Override
                                        public void onTick(long millisUntilFinished) {
                                            tv_countDown.setText(((double)millisUntilFinished)/1000+"");
                                        }

                                        @Override
                                        public void onFinish() {
                                            mCamera.takePicture(null, null, mPicture);
                                            tv_countDown.setText("");
                                        }
                                    }.start();
                                    Toast.makeText(MainActivity.this, "Time interval with PC:"+milisInterval
                                            +"\nActual time:"+Calendar.getInstance().getTimeInMillis()+milisInterval, Toast.LENGTH_LONG).show();
                                    tv_info.append("\nActual time:" + Calendar.getInstance().getTimeInMillis() + milisInterval);
                                }
                                else if(str.startsWith("<SYC>"))
                                {
                                    String str2 =str.substring(5);
                                    double pcTime=Double.valueOf(str2);
                                    Log.v(TAG,"TIME1:"+(long)(pcTime)+"");
                                    Log.v(TAG,"TIME2:"+Calendar.getInstance().getTimeInMillis()+"");
                                    milisInterval=(long)(pcTime-Calendar.getInstance().getTimeInMillis());
                                    Log.v(TAG, "Interval in Millis:" + milisInterval);
                                    tv_info.append("\nTime interval with PC:" + milisInterval);

                                }
                                //takePicture();//takePhoto();
                            }
                        });
                        inStream.reset();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
