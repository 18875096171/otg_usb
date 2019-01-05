package com.example.administiator.test;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.github.mjdev.libaums.partition.Partition;

import java.io.BufferedReader;
import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    //输入的内容
    private EditText u_disk_edt;
    //写入到U盘
    private Button u_disk_write;
    //从U盘读取
    private Button u_disk_read;
    //显示读取的内容
    private TextView u_disk_show;
    //自定义U盘读写权限
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    //当前处接U盘列表
    private UsbMassStorageDevice[] storageDevices;
    //当前U盘所在文件目录
    private UsbFile cFolder;
    private final static String U_DISK_FILE_NAME = "u_disk.txt";
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 100:
                    showToastMsg("保存成功");
                    break;
                case 101:
                    showToastMsg("读取成功");
                    String txt = (String) msg.obj;
                    if (!TextUtils.isEmpty(txt))
                        u_disk_show.setText(txt);
                    else
                        u_disk_show.setText("内容为空");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        registerUDiskReceiver();
        u_disk_write.setOnClickListener(this);
        u_disk_read.setOnClickListener(this);
    }

    private void initViews() {
        u_disk_edt = (EditText) findViewById(R.id.u_disk_edt);
        u_disk_write = (Button) findViewById(R.id.u_disk_write);
        u_disk_read = (Button) findViewById(R.id.u_disk_read);
        u_disk_show = (TextView) findViewById(R.id.u_disk_show);

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.u_disk_write:
                final String content = u_disk_edt.getText().toString().trim();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        saveText2UDisk(content);
                    }
                });

                break;
            case R.id.u_disk_read:
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        readFromUDisk();
                    }
                });

                break;
        }
    }

    private void readFromUDisk() {
        showToastMsg("readFromUDisk");
        try {
            UsbFile[] files = cFolder.listFiles();
            for (UsbFile file : files) {
                if (file.getName().equals(U_DISK_FILE_NAME)) {
                    showToastMsg("文件: " + file.getName());
                    readTxtFromUDisk(file);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

//        try {
//                UsbFile[] usbFiles = cFolder.listFiles();
//                if (null != usbFiles && usbFiles.length > 0) {
//                    for (UsbFile usbFile : usbFiles) {
//                        if (usbFile.getName().equals(U_DISK_FILE_NAME)) {
//                            showToastMsg("开始读数据");
//                            readTxtFromUDisk(usbFile);
//                        }
//                    }
//                }
//
//
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * @description 保存数据到U盘，目前是保存到根目录的
     * @author ldm
     * @time 2017/9/1 17:17
     */
    private void saveText2UDisk(String content) {
        //项目中也把文件保存在了SD卡，其实可以直接把文本读取到U盘指定文件
        File file = FileUtil.getSaveFile(getPackageName()
                        + File.separator + FileUtil.DEFAULT_BIN_DIR,
                U_DISK_FILE_NAME);
        try {
            FileWriter fw = new FileWriter(file);
            fw.write(content);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (null != cFolder) {
            FileUtil.saveSDFile2OTG(file, cFolder);
            mHandler.sendEmptyMessage(100);
        }
    }

    /**
     * @description OTG广播注册
     * @author ldm
     * @time 2017/9/1 17:19
     */
    private void registerUDiskReceiver() {
        //监听otg插入 拔出
        IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mOtgReceiver, usbDeviceStateFilter);
        //注册监听自定义广播
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mOtgReceiver, filter);
    }

    /**
     * @description OTG广播，监听U盘的插入及拔出
     * @author ldm
     * @time 2017/9/1 17:20
     * @param
     */
    private BroadcastReceiver mOtgReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_USB_PERMISSION://接受到自定义广播

                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    //允许权限申请
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            //用户已授权，可以进行读取操作
                            Toast.makeText(MainActivity.this, "可以进行读操作", Toast.LENGTH_LONG).show();
                            readDevice(getUsbMass(usbDevice));
                        } else {
                            showToastMsg("没有插入U盘");
                        }
                    } else {
                        showToastMsg("未获取到U盘权限");
                    }
                    break;

                case UsbManager.ACTION_USB_DEVICE_ATTACHED://接收到U盘设备插入广播
                    UsbDevice device_add = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device_add != null) {
                        //接收到U盘插入广播，尝试读取U盘设备数据
                        redUDiskDevsList();
                        showToastMsg("得到设备列表");
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED://接收到U盘设设备拔出广播
                    showToastMsg("U盘已拔出");
                    break;
            }
        }
    };

    /**
     * @description u盘设备信息读取
     * @author ldm
     * @time 2017/9/1 17:20
     */
    private void redUDiskDevsList() {

        //设备管理器
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        //获取U盘存储设备
        storageDevices = UsbMassStorageDevice.getMassStorageDevices(this);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        //一般手机只有1个OTG插口
        for (UsbMassStorageDevice device : storageDevices) {
            //读取设备是否有权限
            if (usbManager.hasPermission(device.getUsbDevice())) {
                showToastMsg("redUDiskDevsList");
                readDevice(device);
            } else {
                //没有权限，进行申请
                showToastMsg("无权限");
                usbManager.requestPermission(device.getUsbDevice(), pendingIntent);
            }
        }
        if (storageDevices.length == 0) {
            showToastMsg("请插入可用的U盘");
        }
    }

    public UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                showToastMsg("得到设备");
                return device;
            }
        }
        return null;
    }

    //获取设备路径
    private void readDevice(UsbMassStorageDevice device) {
        try {
            device.init();//初始化
            //设备分区
            List<Partition> partitions = device.getPartitions();
            if (partitions.size() == 0) {
                showToastMsg("错误: 读取分区失败");
            } else {
                //文件系统
                FileSystem currentFs = partitions.get(0).getFileSystem();
                currentFs.getVolumeLabel();//可以获取到设备的标识
                showToastMsg("根目录" + currentFs.getRootDirectory());
                //通过FileSystem可以获取当前U盘的一些存储信息，包括剩余空间大小，容量等等
                Log.e("Capacity: ", currentFs.getCapacity() + "");
                Log.e("Occupied Space: ", currentFs.getOccupiedSpace() + "");
                Log.e("Free Space: ", currentFs.getFreeSpace() + "");
                Log.e("Chunk size: ", currentFs.getChunkSize() + "");
                cFolder = currentFs.getRootDirectory();//获取根目录

            }
        } catch (Exception e) {
            e.printStackTrace();
            showToastMsg(e.getMessage());
        }
    }

    private void showToastMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void readTxtFromUDisk(UsbFile usbFile) {
        UsbFile descFile = usbFile;
        //读取文件内容
        InputStream is = new UsbFileInputStream(descFile);
        //读取秘钥中的数据进行匹配
        StringBuffer sb = new StringBuffer();
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(is));
            String read;
            while ((read = bufferedReader.readLine()) != null) {
                sb.append(read);
            }
            Message msg = mHandler.obtainMessage();
            msg.what = 101;
            msg.obj = sb.toString();
            showToastMsg("内容为"+sb);
            mHandler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
