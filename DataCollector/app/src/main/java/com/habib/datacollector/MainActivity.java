package com.habib.datacollector;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import weka.classifiers.lazy.IBk;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class MainActivity extends Activity implements SensorEventListener {

    private static IBk KNNClassifier;
    private static Instances newDataset;
    String inputFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/datacollector/temp_data.arff";
    String onServerFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/datacollector/onServer.csv";
    String onClientFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/datacollector/onClient.csv";

    int onServerCount = 0;
    int onClientCount = 0;

    WifiManager wifiManager;

    boolean onServer = false;
    boolean onClient = false;
    boolean isModelCreated = false;

    String serverMsg = null;
    Socket socket;
    PrintWriter outputPrintWriter = null;
    BufferedReader inputReader = null;

    String data = null;
    String strPrediction = null;

    Map<String, String> tempDataMap = new HashMap<>();

    String accelLastTimestamp = null;
    String gyroLastTimestamp = null;
    String magLastTimestamp = null;

    private boolean haveAccel, haveGyro, haveMag;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope, mMagnetometer;

    TextView tvAccelX, tvAccelY, tvAccelZ;
    TextView tvGyroX, tvGyroY, tvGyroZ;
    TextView tvMagX, tvMagY, tvMagZ;

    TextView tvPredictedClass, serverConnectionStatus, tvTimeDisplay;
    Button btnOnServerStart, btnOnServerStop, btnOnClientStart, btnOnClientStop;

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        tvAccelX = (TextView) findViewById(R.id.accel_x_axis);
        tvAccelY = (TextView) findViewById(R.id.accel_y_axis);
        tvAccelZ = (TextView) findViewById(R.id.accel_z_axis);

        tvGyroX = (TextView) findViewById(R.id.gyro_x_axis);
        tvGyroY = (TextView) findViewById(R.id.gyro_y_axis);
        tvGyroZ = (TextView) findViewById(R.id.gyro_z_axis);

        tvMagX = (TextView) findViewById(R.id.mag_x_axis);
        tvMagY = (TextView) findViewById(R.id.mag_y_axis);
        tvMagZ = (TextView) findViewById(R.id.mag_z_axis);

        tvPredictedClass = (TextView) findViewById(R.id.tvPredictedClass);
        serverConnectionStatus = (TextView) findViewById(R.id.tvConnectionStatus);
        tvTimeDisplay = (TextView) findViewById(R.id.tvTimeDisplay);

        btnOnServerStart = (Button) findViewById(R.id.btnOnServerStart);
        btnOnServerStop = (Button) findViewById(R.id.btnOnServerStop);
        btnOnClientStart = (Button) findViewById(R.id.btnOnClientStart);
        btnOnClientStop = (Button) findViewById(R.id.btnOnClientStop);

        final boolean wifiEnabled = wifiManager.isWifiEnabled();

        btnOnServerStart.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (!wifiEnabled)
                    wifiManager.setWifiEnabled(true);

                onServerCount = 0;

                deleteTempFile(onServerFilePath);

                new CreateSocket().execute();
                onServer = true;
                serverConnectionStatus.setTextColor(Color.parseColor("#00b300"));
                serverConnectionStatus.setText("CONNECTED");
                btnOnServerStart.setEnabled(false);
                btnOnServerStop.setEnabled(true);
                btnOnClientStart.setEnabled(false);
                btnOnClientStop.setEnabled(false);
            }
        });

        btnOnServerStop.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (!wifiEnabled)
                    wifiManager.setWifiEnabled(true);

                try {
                    outputPrintWriter.println("QUIT");
                    outputPrintWriter.close();
                    inputReader.close();
                    socket.close();
                    onServer = false;
                    serverConnectionStatus.setTextColor(Color.RED);
                    serverConnectionStatus.setText("DISCONNECTED");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                tvPredictedClass.setText("NONE!!!");
                tvTimeDisplay.setText("0");
                btnOnServerStart.setEnabled(true);
                btnOnServerStop.setEnabled(true);
                btnOnClientStart.setEnabled(true);
                btnOnClientStop.setEnabled(true);
            }
        });

        btnOnClientStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (wifiEnabled)
                    wifiManager.setWifiEnabled(false);

                onClientCount = 0;

                deleteTempFile(onClientFilePath);

                if (!isModelCreated) {
                    createModel();
                    isModelCreated = true;
                }
                serverConnectionStatus.setTextColor(Color.RED);
                serverConnectionStatus.setText("DISCONNECTED");

                onClient = true;
                btnOnServerStart.setEnabled(false);
                btnOnServerStop.setEnabled(false);
                btnOnClientStart.setEnabled(false);
                btnOnClientStop.setEnabled(true);
            }
        });

        btnOnClientStop.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (!wifiEnabled)
                    wifiManager.setWifiEnabled(true);

                wifiManager.setWifiEnabled(true);
                onClient = false;
                tvPredictedClass.setText("NONE!!!");
                tvTimeDisplay.setText("0");
                btnOnServerStart.setEnabled(true);
                btnOnServerStop.setEnabled(true);
                btnOnClientStart.setEnabled(true);
                btnOnClientStop.setEnabled(true);
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);

        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);

        haveAccel = false;
        haveGyro = false;
        haveMag = false;

    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Calendar calendar = Calendar.getInstance();
        String currentTimestamp = df.format(calendar.getTime());

        Sensor sensor = event.sensor;

        final float[] accelerometer = new float[3];
        final float[] gyroscope = new float[3];
        final float[] magnetic_field = new float[3];

        if ((sensor.getType() == Sensor.TYPE_ACCELEROMETER) && !(currentTimestamp.equals(accelLastTimestamp))) {
            accelerometer[0] = event.values[0];
            accelerometer[1] = event.values[1];
            accelerometer[2] = event.values[2];

            String accel_x = Float.toString(accelerometer[0]);
            String accel_y = Float.toString(accelerometer[1]);
            String accel_z = Float.toString(accelerometer[2]);

            String[] splitParts_1 = accel_x.split("\\.");
            String[] splitParts_2 = accel_y.split("\\.");
            String[] splitParts_3 = accel_z.split("\\.");

            if (splitParts_1[1].length() > 5) {
                splitParts_1[1] = splitParts_1[1].substring(0, 5);
            }

            if (splitParts_2[1].length() > 5) {
                splitParts_2[1] = splitParts_2[1].substring(0, 5);
            }

            if (splitParts_3[1].length() > 5) {
                splitParts_3[1] = splitParts_3[1].substring(0, 5);
            }

            tvAccelX.setText(splitParts_1[0] + "." + splitParts_1[1]);
            tvAccelY.setText(splitParts_2[0] + "." + splitParts_2[1]);
            tvAccelZ.setText(splitParts_3[0] + "." + splitParts_3[1]);

            String accelDataToWrite = Float.toString(accelerometer[0]) + "," + Float.toString(accelerometer[1]) + "," + Float.toString(accelerometer[2]);

            String hasKey = tempDataMap.get(currentTimestamp);
            if (hasKey != null) {
                tempDataMap.put(currentTimestamp, tempDataMap.get(currentTimestamp) + "," + accelDataToWrite);
            } else {
                tempDataMap.put(currentTimestamp, accelDataToWrite);
            }
            haveAccel = true;
            accelLastTimestamp = currentTimestamp;
        } else if ((sensor.getType() == Sensor.TYPE_GYROSCOPE) && !(currentTimestamp.equals(gyroLastTimestamp))) {
            gyroscope[0] = event.values[0];
            gyroscope[1] = event.values[1];
            gyroscope[2] = event.values[2];

            String gyro_x = Float.toString(gyroscope[0]);
            String gyro_y = Float.toString(gyroscope[1]);
            String gyro_z = Float.toString(gyroscope[2]);

            String[] splitParts_1 = gyro_x.split("\\.");
            String[] splitParts_2 = gyro_y.split("\\.");
            String[] splitParts_3 = gyro_z.split("\\.");

            if (splitParts_1[1].length() > 5) {
                splitParts_1[1] = splitParts_1[1].substring(0, 5);
            }

            if (splitParts_2[1].length() > 5) {
                splitParts_2[1] = splitParts_2[1].substring(0, 5);
            }

            if (splitParts_3[1].length() > 5) {
                splitParts_3[1] = splitParts_3[1].substring(0, 5);
            }

            tvGyroX.setText(splitParts_1[0] + "." + splitParts_1[1]);
            tvGyroY.setText(splitParts_2[0] + "." + splitParts_2[1]);
            tvGyroZ.setText(splitParts_3[0] + "." + splitParts_3[1]);

            String gyroDataToWrite = Float.toString(gyroscope[0]) + "," + Float.toString(gyroscope[1]) + "," + Float.toString(gyroscope[2]);

            String hasKey = tempDataMap.get(currentTimestamp);
            if (hasKey != null) {
                tempDataMap.put(currentTimestamp, tempDataMap.get(currentTimestamp) + "," + gyroDataToWrite);
            } else {
                tempDataMap.put(currentTimestamp, gyroDataToWrite);
            }
            haveGyro = true;
            gyroLastTimestamp = currentTimestamp;
        } else if ((sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) && !(currentTimestamp.equals(magLastTimestamp))) {
            magnetic_field[0] = event.values[0];
            magnetic_field[1] = event.values[1];
            magnetic_field[2] = event.values[2];

            String mag_x = Float.toString(magnetic_field[0]);
            String mag_y = Float.toString(magnetic_field[1]);
            String mag_z = Float.toString(magnetic_field[2]);

            String[] splitParts_1 = mag_x.split("\\.");
            String[] splitParts_2 = mag_y.split("\\.");
            String[] splitParts_3 = mag_z.split("\\.");

            if (splitParts_1[1].length() > 5) {
                splitParts_1[1] = splitParts_1[1].substring(0, 5);
            }

            if (splitParts_2[1].length() > 5) {
                splitParts_2[1] = splitParts_2[1].substring(0, 5);
            }

            if (splitParts_3[1].length() > 5) {
                splitParts_3[1] = splitParts_3[1].substring(0, 5);
            }

            tvMagX.setText(splitParts_1[0] + "." + splitParts_1[1]);
            tvMagY.setText(splitParts_2[0] + "." + splitParts_2[1]);
            tvMagZ.setText(splitParts_3[0] + "." + splitParts_3[1]);

            String magDataToWrite = Float.toString(magnetic_field[0]) + "," + Float.toString(magnetic_field[1]) + "," + Float.toString(magnetic_field[2]);

            String hasKey = tempDataMap.get(currentTimestamp);
            if (hasKey != null) {
                tempDataMap.put(currentTimestamp, tempDataMap.get(currentTimestamp) + "," + magDataToWrite);
            } else {
                tempDataMap.put(currentTimestamp, magDataToWrite);
            }
            haveMag = true;
            magLastTimestamp = currentTimestamp;
        }

        if (haveAccel && haveGyro && haveMag) {

            data = tempDataMap.get(currentTimestamp) + ",?";

            if (onClient)
                new RunModelOnPhone().execute(data);
            if (onServer)
                new RunModelOnServer().execute(data);

            haveAccel = false;
            haveGyro = false;
            haveMag = false;
        }
    }

    public class CreateSocket extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            try {
                socket = new Socket("10.80.64.74", 5000);
                inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                serverMsg = inputReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            serverConnectionStatus.setText(serverMsg);
            return;
        }
    }

    public class RunModelOnServer extends AsyncTask<String, Integer, String> {

        long startTime;
        long endTime;
        long totalTime;

        @Override
        protected String doInBackground(String... params) {
            try {
                outputPrintWriter = new PrintWriter(socket.getOutputStream(), true);
                inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                startTime = System.currentTimeMillis();
                outputPrintWriter.println(params[0]);
                serverMsg = inputReader.readLine();
                endTime = System.currentTimeMillis();
                totalTime = endTime - startTime;
                onServerCount++;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            tvPredictedClass.setText(serverMsg);
            tvTimeDisplay.setText(totalTime + " ms");

            File sdCard = Environment.getExternalStorageDirectory();
            File directory = new File(sdCard.getAbsolutePath() + "/datacollector");
            directory.mkdirs();

            File file = new File(directory, "onServer.csv");
            FileOutputStream fOut = null;
            try {
                fOut = new FileOutputStream(file, true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            OutputStreamWriter osw = new OutputStreamWriter(fOut);
            try {
                osw.write(onServerCount + "," + totalTime + "\r\n");
                osw.flush();
                osw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return;
        }
    }

    public class RunModelOnPhone extends AsyncTask<String, Integer, String> {

        long startTime;
        long endTime;
        long totalTime;

        @Override
        protected String doInBackground(String... params) {
            tempStoreNewInstance(params[0]);
            loadDataset(inputFilePath);
            startTime = System.currentTimeMillis();
            strPrediction = getClassValue();
            endTime = System.currentTimeMillis();
            totalTime = endTime - startTime;
            onClientCount++;
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (strPrediction == null) {
                tvPredictedClass.setText("NONE!!!");
                tvTimeDisplay.setText("0");
            } else {
                tvPredictedClass.setText(strPrediction);
                tvTimeDisplay.setText(totalTime + " ms");

                File sdCard = Environment.getExternalStorageDirectory();
                File directory = new File(sdCard.getAbsolutePath() + "/datacollector");
                directory.mkdirs();

                File file = new File(directory, "onClient.csv");
                FileOutputStream fOut = null;
                try {
                    fOut = new FileOutputStream(file, true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                OutputStreamWriter osw = new OutputStreamWriter(fOut);
                try {
                    osw.write(onClientCount + "," + totalTime + "\r\n");
                    osw.flush();
                    osw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            deleteTempFile(inputFilePath);
            return;
        }
    }

    public void tempStoreNewInstance(String dataToWrite) {

        File sdCard = Environment.getExternalStorageDirectory();
        File directory = new File(sdCard.getAbsolutePath() + "/datacollector");
        directory.mkdirs();

        File file = new File(directory, "temp_data.arff");
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        OutputStreamWriter osw = new OutputStreamWriter(fOut);
        try {
            osw.write("@relation temp_data\r\n");
            osw.write("\r\n");
            osw.write("@attribute Ax numeric\r\n");
            osw.write("@attribute Ay numeric\r\n");
            osw.write("@attribute Az numeric\r\n");
            osw.write("@attribute Gx numeric\r\n");
            osw.write("@attribute Gy numeric\r\n");
            osw.write("@attribute Gz numeric\r\n");
            osw.write("@attribute Mx numeric\r\n");
            osw.write("@attribute My numeric\r\n");
            osw.write("@attribute Mz numeric\r\n");
            osw.write("@attribute Activity {Downstairs,Running,Sitting,Standing,Upstairs,Walking}\r\n");
            osw.write("\r\n");
            osw.write("@data\r\n");
            osw.write(dataToWrite + "\r\n");
            osw.flush();
            osw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createModel() {

        // load model from assets directory
        try {
            KNNClassifier = (IBk) weka.core.SerializationHelper.read(getAssets().open("KNNModel.model"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadDataset(String datasetPath) {
        // load new dataset
        try {
            DataSource newDataSource = new DataSource(datasetPath);
            newDataset = newDataSource.getDataSet();
            // set class index
            newDataset.setClassIndex(newDataset.numAttributes() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getClassValue() {

        String classValue = null;

        try {
            // get instance object of current instance
            Instance newInst = newDataset.instance(0);
            // call classifyInstance, which returns a double value for the class
            double doubleClassValue = KNNClassifier.classifyInstance(newInst);
            // use this value to get string value of the predicted class
            classValue = newDataset.classAttribute().value((int) doubleClassValue);
            //System.out.println("Class predicted: " + classValue);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return classValue;
    }

    public void deleteTempFile(String filePath) {
        try {
            new File(filePath).delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
