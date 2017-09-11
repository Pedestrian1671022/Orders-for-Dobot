package com.example.pedestrian_username.orders_for_dobot;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private EditText editText;
    private Button connectButton;
    private Button disconnectButton;
    private SpeechRecognizer mIat;
    private TextView iat_textView;
    private Button iat_button;
    private Toast mToast;
    int ret = 0;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    @SuppressLint("ShowToast")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SpeechUtility.createUtility(MainActivity.this, SpeechConstant.APPID+"=581c7563");

        editText = (EditText) findViewById(R.id.ipAddress);
        connectButton = (Button) findViewById(R.id.connect);
        disconnectButton = (Button) findViewById(R.id.disconnect);
        iat_textView = (TextView) findViewById(R.id.result);
        iat_button = (Button) findViewById(R.id.start);
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
        mIat_setParams();

        connectButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            socket = new Socket(editText.getText().toString(), 9500);
                            mToast.setText("Socket已经链接");
                            mToast.show();
                        } catch (IOException e) {
                            mToast.setText("Socket链接出现问题，原因可能是：Invalid Ip Address");
                            mToast.show();
                        }
                    }
                }.start();
            }
        });

        disconnectButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view){
                try {
                    iat_textView.setText(null);
                    socket.close();
                    mToast.setText("Socket已经断开");
                    mToast.show();
                } catch (IOException e) {
                    mToast.setText("socket关闭出现问题！");
                    mToast.show();
                }
            }
        });

        iat_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iat();
            }
        });
    }

    public void iat(){
        // 开始听写
        // 如何判断一次听写结束：OnResult isLast=true 或者 onError
        iat_textView.setText(null);// 清空显示内容
        mIatResults.clear();
        // 不显示听写对话框
        ret = mIat.startListening(mRecognizerListener);
        if (ret != ErrorCode.SUCCESS) {
            mToast.setText("听写失败,错误码：" + ret);
            mToast.show();
        } else {
            mToast.setText("请开始说话");
            mToast.show();
        }
    }

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                mToast.setText("初始化失败，错误码：" + code);
                mToast.show();
            }
        }
    };

    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            mToast.setText("语音听写开始");
            mToast.show();
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
            // 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
            mToast.setText(error.getPlainDescription(true));
            mToast.show();
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            mToast.setText("语音听写结束");
            mToast.show();
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());

            String sn = null;
            // 读取json结果中的sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString());
                sn = resultJson.optString("sn");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mIatResults.put(sn, text);

            StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults.get(key));
            }
            if (isLast) {
                iat_textView.setText(resultBuffer.toString());
                try {
                    PrintStream send = new PrintStream(socket.getOutputStream(), true, "GB2312");
//                    PrintStream send = new PrintStream(socket.getOutputStream(), true, "utf-8");
                    send.print(resultBuffer.toString()+"\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            mToast.setText("当前正在说话，音量大小：" + volume);
            mToast.show();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    public void mIat_setParams() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        // 设置语言
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS,"4000");
        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS,"1000");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT,"0");
        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/iat.wav");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出时释放连接
        mIat.cancel();
        mIat.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
