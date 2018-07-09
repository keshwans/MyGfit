package com.newtech.mygfit;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001;
    private static final String TAG = MainActivity.class.getSimpleName();

    private int mSensorsStepCount = 0;
    private TextView mStepsSensor;
    private TextView mStepsToday;
    private TextView mStepsWeekly;
    private Button mRefresh;
    private OnDataPointListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState != null) {
            mSensorsStepCount = savedInstanceState.getInt("sensor_steps");
        }
        mStepsSensor = findViewById(R.id.steps_sensor);
        mStepsToday = findViewById(R.id.steps_today);
        mStepsWeekly = findViewById(R.id.steps_week);
        mRefresh = findViewById(R.id.refresh);
        mRefresh.setEnabled(false);
        mRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateDashboard();
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

    }


    private void initializeGoogleFit() {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA)
                .addDataType(DataType.TYPE_LOCATION_SAMPLE)
                .addDataType(DataType.TYPE_ACTIVITY_SAMPLES)
                .build();

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this, // your activity
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions);
        } else {
            findFitnessDataSources();
            startRecording();
            updateDashboard();

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
                findFitnessDataSources();
                startRecording();
                updateDashboard();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeGoogleFit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterFitnessDataListener();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("sensor_steps", mSensorsStepCount);
    }

    private void startRecording() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .addOnCompleteListener(
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Log.i(TAG, "Successfully subscribed!");
                                } else {
                                    Log.w(TAG, "There was a problem subscribing.", task.getException());
                                }
                            }
                        });


    }

    private void updateDashboard() {
        mRefresh.setEnabled(false);
        readStepsCountForToday();
        readStepsCountForWeek();
        mStepsSensor.setText("Sensor: " + mSensorsStepCount);

        mRefresh.setEnabled(true);

    }

    /**
     * Finds available data sources and attempts to register on a specific {@link DataType}.
     */
    private void findFitnessDataSources() {
        // [START find_data_sources]
        // Note: Fitness.SensorsApi.findDataSources() requires the ACCESS_FINE_LOCATION permission.
        Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .findDataSources(
                        new DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                                .setDataSourceTypes(DataSource.TYPE_RAW)
                                .build())
                .addOnSuccessListener(
                        new OnSuccessListener<List<DataSource>>() {
                            @Override
                            public void onSuccess(List<DataSource> dataSources) {
                                for (DataSource dataSource : dataSources) {
                                    Log.i(TAG, "Data source found: " + dataSource.toString());
                                    Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                                    // Let's register a listener to receive Activity data!
                                    if (dataSource.getDataType().equals(DataType.TYPE_STEP_COUNT_DELTA)
                                            && mListener == null) {
                                        Log.i(TAG, "Data source for TYPE_STEP_COUNT_DELTA found!  Registering.");
                                        registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_DELTA);
                                    }
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "failed", e);
                            }
                        });
    }

    /**
     * Registers a listener with the Sensors API for the provided {@link DataSource} and {@link
     * DataType} combo.
     */
    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        mListener =
                new OnDataPointListener() {
                    @Override
                    public void onDataPoint(DataPoint dataPoint) {
                        for (Field field : dataPoint.getDataType().getFields()) {
                            Value val = dataPoint.getValue(field);
                            Log.i(TAG, "Detected DataPoint field: " + field.getName());
                            Log.i(TAG, "Detected DataPoint value: " + val);

                            mSensorsStepCount += val.asInt();
                        }
                    }
                };

        Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .add(
                        new SensorRequest.Builder()
//                                .setDataSource(dataSource) // Optional but recommended for custom data sets.
                                .setDataType(dataType) // Can't be omitted.
                                .setSamplingRate(1, TimeUnit.SECONDS)
                                .build(),
                        mListener)
                .addOnCompleteListener(
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Log.i(TAG, "Listener registered!");
                                } else {
                                    Log.e(TAG, "Listener not registered.", task.getException());
                                }
                            }
                        });
        // [END register_data_listener]
    }

    /**
     * Unregisters the listener with the Sensors API.
     */
    private void unregisterFitnessDataListener() {
        if (mListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return;
        }

        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .remove(mListener)
                .addOnCompleteListener(
                        new OnCompleteListener<Boolean>() {
                            @Override
                            public void onComplete(@NonNull Task<Boolean> task) {
                                if (task.isSuccessful() && task.getResult()) {
                                    Log.i(TAG, "Listener was removed!");
                                } else {
                                    Log.i(TAG, "Listener was not removed.");
                                }
                                mListener = null;
                            }
                        });
        // [END unregister_data_listener]
    }

    private void readStepsCountForToday() {

        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);

        long endTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startTime = cal.getTimeInMillis();

        final DataReadRequest readRequest = new DataReadRequest.Builder()
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

//        DataReadResult dataReadResult =
//                Fitness.HistoryApi.readData(mGoogleApiFitnessClient, readRequest).await(1, TimeUnit.MINUTES);

        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener() {
                    @Override
                    public void onSuccess(Object o) {
                        Log.d(TAG, "onSuccess()");
                        if (o instanceof DataReadRequest) {
                            DataReadResponse dataReadResponse = (DataReadResponse) o;
                            DataSet stepData = dataReadResponse.getDataSet(DataType.TYPE_STEP_COUNT_DELTA);

                            int totalSteps = 0;

                            for (DataPoint dp : stepData.getDataPoints()) {
                                for (Field field : dp.getDataType().getFields()) {
                                    int steps = dp.getValue(field).asInt();

                                    totalSteps += steps;

                                }
                            }
                        }
                    }

                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.d(TAG, "onSuccess()");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure()", e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        Log.d(TAG, "onComplete()");

                        DataReadResponse dataReadResponse = (DataReadResponse) task.getResult();
                        DataSet stepData = dataReadResponse.getDataSet(DataType.TYPE_STEP_COUNT_DELTA);

                        int totalSteps = 0;

                        for (DataPoint dp : stepData.getDataPoints()) {
                            for (Field field : dp.getDataType().getFields()) {
                                int steps = dp.getValue(field).asInt();
                                totalSteps += steps;
                            }
                        }
                        Log.i(TAG, "total steps for this week: " + totalSteps);
                        mStepsToday.setText("Total Steps today:" + totalSteps);
                    }
                });
    }

    private void readStepsCountForWeek1() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

//        DataReadResult dataReadResult =
//                Fitness.HistoryApi.readData(mGoogleApiFitnessClient, readRequest).await(1, TimeUnit.MINUTES);

        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener() {
                    @Override
                    public void onSuccess(Object o) {
                        Log.d(TAG, "onSuccess()");
                        if (o instanceof DataReadRequest) {
                            DataReadResponse dataReadResponse = (DataReadResponse) o;
                            DataSet stepData = dataReadResponse.getDataSet(DataType.AGGREGATE_STEP_COUNT_DELTA);

                            int totalSteps = 0;

                            for (DataPoint dp : stepData.getDataPoints()) {
                                for (Field field : dp.getDataType().getFields()) {
                                    int steps = dp.getValue(field).asInt();
                                    totalSteps += steps;
                                }
                            }
                        }
                    }

                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.d(TAG, "onSuccess()");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure()", e);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        Log.d(TAG, "onComplete()");

                        DataReadResponse dataReadResponse = (DataReadResponse) task.getResult();
                        DataSet stepData = dataReadResponse.getDataSet(DataType.AGGREGATE_STEP_COUNT_DELTA);

                        int totalSteps = 0;

                        for (DataPoint dp : stepData.getDataPoints()) {
                            for (Field field : dp.getDataType().getFields()) {
                                int steps = dp.getValue(field).asInt();
                                totalSteps += steps;
                            }
                        }
                        Log.i(TAG, "total steps for this week: " + totalSteps);
                        mStepsWeekly.setText("Total steps for week: " + totalSteps);
                    }
                });
    }

    private void readStepsCountForWeek() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this)).readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        List<Bucket> buckets = dataReadResponse.getBuckets();

                        int totalSteps = 0;

                        for (Bucket bucket : buckets) {

                            List<DataSet> dataSets = bucket.getDataSets();

                            for (DataSet dataSet : dataSets) {
//                                if (dataSet.getDataType() == DataType.TYPE_STEP_COUNT_DELTA) {


                                for (DataPoint dp : dataSet.getDataPoints()) {
                                    for (Field field : dp.getDataType().getFields()) {
                                        int steps = dp.getValue(field).asInt();
                                        totalSteps += steps;
                                    }
                                }
                            }
//                            }
                        }
                        Log.i(TAG, "total steps for this week: " + totalSteps);
                        mStepsWeekly.setText("Total steps for week: " + totalSteps);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        List<Bucket> buckets = ((DataReadResponse) task.getResult()).getBuckets();

                        int totalSteps = 0;

                        for (Bucket bucket : buckets) {

                            List<DataSet> dataSets = bucket.getDataSets();

                            for (DataSet dataSet : dataSets) {
//                                if (dataSet.getDataType() == DataType.TYPE_STEP_COUNT_DELTA) {


                                for (DataPoint dp : dataSet.getDataPoints()) {
                                    for (Field field : dp.getDataType().getFields()) {
                                        int steps = dp.getValue(field).asInt();
                                        totalSteps += steps;
                                    }
                                }
                            }
//                            }
                        }
                        Log.i(TAG, "total steps for this week: " + totalSteps);
                        mStepsWeekly.setText("Total steps for week: " + totalSteps);
                    }
                });
    }
}
