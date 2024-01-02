package com.example.audiotracksample;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.InputStream;
import java.nio.ByteBuffer;

import androidx.appcompat.app.AppCompatActivity;

class Rotation {
    public float rotationX;
    public float rotationY;
    public float rotationZ;
    public Rotation(){

    }
}

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioTrackSample";

    // hello_world.wav のサンプリングレート
    private int mSampleRate;
    private int mChannelConfig;
    private int mAudioFormat;
    private int mAllDataSize; // 全データサイズ
    private int mHeaderSize; // mAllDataSize - wavDataSize
    private int mStartIndex;
    private int mEndIndex;
    private boolean mIsPlaying; // 再生中 = true, 停止中 = false
    private final static int SOUND_DATA_ID = R.raw.sinwave_440hz;
    private AudioTrack mAudioTrack;
    byte[] mWavData;
    Button mPlayButton;

    // ジャイロセンサー関連
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private TextView mSensorTextView;

    private static final float MAX_DIFF = 0.3F;
    private float mGain;
    private Rotation mRotationSaveData = new Rotation();
    private Rotation mRotationData = new Rotation();
    private TextView mSensorSaveTextView;
    private TextView mSensorSaveDiffTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // センサーを取得
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        mSensorTextView = findViewById(R.id.sensorTextView);
        mSensorSaveTextView = findViewById(R.id.sensorSaveTextView);
        mSensorSaveDiffTextView = findViewById(R.id.sensorSaveDiffTextView);

        // リスナーをボタンに登録
        // expression lambda
        mPlayButton = findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(v-> {
            try {
                if (!mIsPlaying){
                    mIsPlaying = true;
                    Log.d(TAG, "再生開始");
                    mPlayButton.setText("Stop");
                    // 非同期でAudioTrackの再生を開始
                    new WavPlayTask().execute();
                }
                else {
                    mIsPlaying = false;
                    Log.d(TAG, "再生停止");
                    mPlayButton.setText("Play");
                    mSensorSaveDiffTextView.setText("");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // センサーリスナー登録
        mSensorManager.registerListener(mSensorEventListener, mRotationSensor, SensorManager.SENSOR_DELAY_FASTEST);

        // wavデータセット
        setWavData();

//        for (int i = 0; i < mWavData.length; i+=2){
//            Log.d(TAG, "[" + i + ", " + (i+1) + "] => [" + Integer.toHexString(mWavData[i]) + ", " + Integer.toHexString(mWavData[i+1]) + "]");
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // センサーリスナー登録解除
        mSensorManager.unregisterListener(mSensorEventListener);
        mSensorSaveDiffTextView.setText("");

        if (mAudioTrack != null){
            mIsPlaying = false;
            mPlayButton.setText("Play");
            mAudioTrack.stop();
            mAudioTrack.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // センサーリスナー登録解除
        mSensorManager.unregisterListener(mSensorEventListener);
        mSensorSaveDiffTextView.setText("");

        if (mAudioTrack != null){
            mIsPlaying = false;
            mPlayButton.setText("Play");
            mAudioTrack.stop();
            mAudioTrack.release();
        }
    }

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR){
                // 再生中でなければ更新する
                if (!mIsPlaying){
                    mRotationSaveData.rotationX = event.values[0];
                    mRotationSaveData.rotationY = event.values[1];
                    mRotationSaveData.rotationZ = event.values[2];
                    String strSaveTmp = "ジャイロセンサー(保存値)\nX: " + mRotationSaveData.rotationX +
                            "\nY: " + mRotationSaveData.rotationY + "\nZ: " + mRotationSaveData.rotationZ;
                    mSensorSaveTextView.setText(strSaveTmp);
                }
                else {
                    // ゲイン計算
                    calGain();
                }
                mRotationData.rotationX = event.values[0];
                mRotationData.rotationY = event.values[1];
                mRotationData.rotationZ = event.values[2];

                String strTmp = "ジャイロセンサー\nX: " + mRotationData.rotationX + "\nY: " +
                        mRotationData.rotationY + "\nZ: " + mRotationData.rotationZ;
                mSensorTextView.setText(strTmp);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void calGain(){
        // ジャイロセンサー値に応じて、ゲインを求める
        float diffAbsY = Math.abs(mRotationSaveData.rotationY - mRotationData.rotationY);
        mGain = diffAbsY / MAX_DIFF;
        if (mGain >= 1.0F){
            mGain = 1.0F;
        }
        String strTemp = "絶対値:" + diffAbsY + "\nゲイン:" + mGain;
        mSensorSaveDiffTextView.setText(strTemp);
    }

    private class WavPlayTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                wavPlay();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    private void setWavData(){
        try {
            // TODO:音声データの再読込時に波形が切り替わるのでプツッとなるのを対処する(https://note.com/nc_kanai/n/n874a4be11aef)
            // TODO:ゲイン変更頻度は固定でいいのか？
            // 再生読み込み用
            InputStream inputStream = getResources().openRawResource(SOUND_DATA_ID);
            // ヘッダ解析用
            InputStream inputStreamForHeader = getResources().openRawResource(SOUND_DATA_ID);
            byte[] headerBuffer = new byte[inputStreamForHeader.available()];
            mAllDataSize = inputStreamForHeader.read(headerBuffer);
            checkWaveHeaderData(headerBuffer);

            // ヘッダーをスキップ(inputをヘッダー分、読み込んでスキップしてる)
            // データによっては、ヘッダが44byteとは限らない、、。
            // だが、必ず、fmt と data のチャンクIDは必ずある
            // data から取れる音声データのサイズ以外をヘッダとする
            if (mStartIndex != 0){
                headerBuffer = new byte[mStartIndex];
                inputStream.read(headerBuffer, 0, mStartIndex);
            }
            else {
                headerBuffer = new byte[mHeaderSize];
                inputStream.read(headerBuffer, 0, mHeaderSize);
            }

            int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannelConfig, mAudioFormat);
            // http://bril-tech.blogspot.com/2015/08/androidaudiotrack.html
            mAudioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(mSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();

            // 読み込まれた分のサイズになっている
            mWavData = new byte[inputStream.available()]; // wavDataのサイズで初期化
            inputStream.read(mWavData);

            // 閉じる
            inputStreamForHeader.close();
            inputStream.close();

        } catch (Exception e){
            Log.d(TAG, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private void wavPlay() throws Exception {

        try {
            // 再生準備開始
            mAudioTrack.play();

            // 4byte配列でないと、ByteBufferでエラーが出る
//            byte[] wavBlockByteData = {0,0,0,0};
//            ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
            while (mIsPlaying){
                for (int i = 0; i < mWavData.length; i+=2){
                    // 再生停止なら抜ける
                    if (!mIsPlaying){
                        break;
                    }
                    // エンド地点なら抜ける
                    if (i == mEndIndex){
                        break;
                    }

                    // リトルエンディアンで後ろから並べる
//                wavBlockByteData[2] = wavData[i + 1];
//                wavBlockByteData[3] = wavData[i];

//                intBuffer.clear();
//                intBuffer.put(wavBlockByteData);
//                intBuffer.flip();
//                long wavBlockIntData = (long) intBuffer.getInt();
//                double gain = 1.0;
//                long result;
//                result = (long) (wavBlockIntData * gain); // gain適用
//
//                // intをbyteに変換
//                intBuffer.clear();
//                intBuffer.putInt((int) result); // intをBufferにセット
//                intBuffer.flip(); // Bufferを読み取りモードにセット
//                intBuffer.get(wavBlockByteData); // Bufferからbyteを取得
//
//                // wavDataを置き換え
//                wavData[i + 1] = wavBlockByteData[2];
//                wavData[i] = wavBlockByteData[3];

                    // 逐次writeする場合
                    byte[] data = {mWavData[i], mWavData[i+1]};
                    // 1000の倍数ごとにGainを変更
                    // これぐらいの変更頻度でないとブツブツ音になる
                    if (i % 1000 == 0){
                        // ジャイロに応じたゲインを適用
                        mAudioTrack.setVolume(mGain);
                    }
                    mAudioTrack.write(data, 0, data.length);
                }
            }
            // mAudioTrack.setVolume(1.0F);
            // まとめて再生する場合
            // mAudioTrack.write(wavData, 0, wavDataSize);
            Log.d(TAG, "audioTrackを停止");
            mAudioTrack.stop();
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    private void checkWaveHeaderData(byte[] wavHeaderData){
        // https://qiita.com/goodclues/items/67a20140fd7c205872d6
        // https://www.youfit.co.jp/archives/1418

        // TODO:解析した値でAudioTrackの値をセットするようにすること

        // fmt と data は、固定値で含まれているので基準のIndexとなる
        int fmtIndex = 0;
        // 検索対象のヘッダデータは4バイトで意味を表すもののため
        for (int i = 0; i < wavHeaderData.length - 4; i++){
            // ASCⅡ
            // 'fmt ' => f:0x66, m:0x6d, t:0x74, ' ':0x20
            if (wavHeaderData[i] == 0x66 && wavHeaderData[i+1] == 0x6d
            && wavHeaderData[i+2] == 0x74 && wavHeaderData[i+3] == 0x20){
                fmtIndex = i;
                Log.d(TAG, "fmt index : " + fmtIndex);
                break;
            }
        }
        if (fmtIndex == 0){
            Log.e(TAG, "Not found fmt index");
        }

        int dataIndex = 0;
        for (int i = 0; i < wavHeaderData.length - 4; i++){
            // ASCⅡ
            // 'data' => d:0x64, a:0x61, t:0x74, a:0x61
            if (wavHeaderData[i] == 0x64 && wavHeaderData[i+1] == 0x61
            && wavHeaderData[i+2] == 0x74 && wavHeaderData[i+3] == 0x61){
                dataIndex = i;
                Log.d(TAG, "data index : "+ dataIndex);
                break;
            }
        }
        if (dataIndex == 0){
            Log.e(TAG, "Not found data index");
        }

        if (fmtIndex != 0){
            // チャンネル
            // 上位バイト モノラル:0x01, ステレオ:0x02
            // 下位バイト 0x00
            byte wavChannelUpperByte = wavHeaderData[fmtIndex+10];
            byte wavChannelLowByte = wavHeaderData[fmtIndex+11];
            Log.d(TAG, "チャンネル(上位バイト):0x" + Integer.toHexString(wavChannelUpperByte));
            Log.d(TAG, "チャンネル(下位バイト):0x" + Integer.toHexString(wavChannelLowByte));
            // 4byteで収めないとエラーが出る
            // 後ろから格納 リトルエンディアンてことかな
            byte[] wavChannelByte = {0, 0, wavChannelLowByte, wavChannelUpperByte};
            int wavChannel = ByteBuffer.wrap(wavChannelByte).getInt();
            Log.d(TAG, "チャンネル:" + Integer.toHexString(wavChannel));
            switch (wavChannelUpperByte){
                case 1:
                    Log.d(TAG, "チャンネル:モノラル");
                    mChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
                    break;
                case 2:
                    Log.d(TAG, "チャンネル:ステレオ");
                    mChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                    break;
            }

            // サンプリングレート
            // 44100Hz => 0x44AC0000
            byte wavSamplingRateUpperByte1 = wavHeaderData[fmtIndex+12];
            byte wavSamplingRateLowByte1 = wavHeaderData[fmtIndex+13];
            byte wavSamplingRateUpperByte2 = wavHeaderData[fmtIndex+14];
            byte wavSamplingRateLowByte2 = wavHeaderData[fmtIndex+15];
            Log.d(TAG, "サンプリングレート(1byte):0x"+Integer.toHexString(wavSamplingRateUpperByte1));
            Log.d(TAG, "サンプリングレート(2byte):0x"+Integer.toHexString(wavSamplingRateLowByte1));
            Log.d(TAG, "サンプリングレート(3byte):0x"+Integer.toHexString(wavSamplingRateUpperByte2));
            Log.d(TAG, "サンプリングレート(4byte):0x"+Integer.toHexString(wavSamplingRateLowByte2));
            // リトルエンディアン
            byte[] wavSamplingRateByte = {wavSamplingRateLowByte2, wavSamplingRateUpperByte2,
                    wavSamplingRateLowByte1, wavSamplingRateUpperByte1};
            int wavSamplingRate = ByteBuffer.wrap(wavSamplingRateByte).getInt();
            Log.d(TAG, "サンプリングレート:"+wavSamplingRate+"Hz");
            mSampleRate = wavSamplingRate;

            // ブロックサイズ
            // (チャンネル数 * ビット数) / 8bit
            // モノラル 16bit => (1 * 16) / 8 = 2byte
            // ステレオ 16bit => (2 * 16) / 8 = 4byte
            byte wavBlockSizeByte = wavHeaderData[fmtIndex+20];
            Log.d(TAG, "ブロックサイズ:0x"+Integer.toHexString(wavBlockSizeByte));
            switch ((int)wavBlockSizeByte){
                case 0x02:
                    Log.d(TAG, "ブロックサイズ:2byte");
                    break;
                case 0x04:
                    Log.d(TAG, "ブロックサイズ:4byte");
                    break;
            }

            // ビット数
            // 8bit(1byte) => 0x0800
            // 16bit(2byte) => 0x1000
            byte wavBitSize = wavHeaderData[fmtIndex+22];
            Log.d(TAG, "ビット数:0x"+Integer.toHexString(wavBitSize));
            int wavBitByteSize = wavBitSize / 8; // バイト換算
            Log.d(TAG, "ビット数:"+wavBitByteSize+"byte");
            switch ((int)wavBitSize){
                case 0x08:
                    Log.d(TAG, "ビット数:8bit");
                    mAudioFormat = AudioFormat.ENCODING_PCM_8BIT;
                    break;
                case 0x10:
                    Log.d(TAG, "ビット数:16bit");
                    mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
                    break;
            }
        }
        if (dataIndex != 0){
            // wavデータサイズ
            byte wavDataSizeUpperByte1 = wavHeaderData[dataIndex+4];
            byte wavDataSizeLowByte1 = wavHeaderData[dataIndex+5];
            byte wavDataSizeUpperByte2 = wavHeaderData[dataIndex+6];
            byte wavDataSizeLowByte2 = wavHeaderData[dataIndex+7];
            Log.d(TAG, "データサイズ(1byte):0x"+Integer.toHexString(wavDataSizeUpperByte1));
            Log.d(TAG, "データサイズ(2byte):0x"+Integer.toHexString(wavDataSizeLowByte1));
            Log.d(TAG, "データサイズ(3byte):0x"+Integer.toHexString(wavDataSizeUpperByte2));
            Log.d(TAG, "データサイズ(4byte):0x"+Integer.toHexString(wavDataSizeLowByte2));

            byte[] wavDataSizeByte = {wavDataSizeLowByte2, wavDataSizeUpperByte2,
                                    wavDataSizeLowByte1, wavDataSizeUpperByte1};
            int wavDataSize = ByteBuffer.wrap(wavDataSizeByte).getInt();
            Log.d(TAG, "データサイズ(総バイト数) : "+wavDataSize);
            // ヘッダサイズ(データ開始地点までとする)
            mHeaderSize = mAllDataSize - wavDataSize;
        }

        // スタート探し
        for (int i = mHeaderSize; i < wavHeaderData.length; i+=2){
            if (wavHeaderData[i] != 0x00 || wavHeaderData[i+1] != 0x00){
                mStartIndex = i;
                Log.d(TAG, "開始地点 : " + mStartIndex);
                break;
            }
        }
        // エンド探し
        for (int i = mAllDataSize-1; i > mHeaderSize; i-=2){
            if (wavHeaderData[i-1] != 0x00 || wavHeaderData[i] != 0x00){
                mEndIndex = i;
                Log.d(TAG, "終了地点 : " + mEndIndex);
                break;
            }
        }
    }
}