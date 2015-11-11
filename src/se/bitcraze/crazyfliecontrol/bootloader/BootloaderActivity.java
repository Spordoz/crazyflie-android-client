package se.bitcraze.crazyfliecontrol.bootloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import se.bitcraze.crazyfliecontrol.bootloader.FirmwareDownloader.DownloadListener;
import se.bitcraze.crazyfliecontrol2.R;
import se.bitcraze.crazyfliecontrol2.UsbLinkAndroid;
import se.bitcraze.crazyflielib.bootloader.Bootloader;
import se.bitcraze.crazyflielib.bootloader.Bootloader.BootloaderListener;
import se.bitcraze.crazyflielib.bootloader.Target.TargetTypes;
import se.bitcraze.crazyflielib.bootloader.Utilities.BootVersion;
import se.bitcraze.crazyflielib.crazyradio.RadioDriver;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class BootloaderActivity extends Activity {

    private static final String LOG_TAG = "Bootloader";
    private ImageButton mCheckUpdateButton;
    private ImageButton mFlashFirmwareButton;
    private Spinner mFirmwareSpinner;
    private CustomSpinnerAdapter mSpinnerAdapter;
    private ProgressBar mProgressBar;
    private TextView mStatusLineTextView;

    private Firmware mSelectedFirmware = null;
    private FirmwareDownloader mFirmwareDownloader;
    private Bootloader mBootloader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bootloader);
        mCheckUpdateButton = (ImageButton) findViewById(R.id.bootloader_checkUpdate);
        mFlashFirmwareButton = (ImageButton) findViewById(R.id.bootloader_flashFirmware);
        mFirmwareSpinner = (Spinner) findViewById(R.id.bootloader_firmwareSpinner);
        mProgressBar = (ProgressBar) findViewById(R.id.bootloader_progressBar);
        mStatusLineTextView = (TextView) findViewById(R.id.bootloader_statusLine);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        initializeFirmwareSpinner();
        mFirmwareDownloader = new FirmwareDownloader(this);

        this.registerReceiver(mFirmwareDownloader.onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mFirmwareDownloader.onComplete);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mFirmwareDownloader.isFileAlreadyDownloaded(FirmwareDownloader.RELEASES_JSON)) {
            mFirmwareDownloader.checkForFirmwareUpdate();
        } else {
            mFlashFirmwareButton.setEnabled(false);
            //TODO: force update of spinner adapter even though firmware list is empty
        }
    }

    public void checkForFirmwareUpdate(View view) {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            mStatusLineTextView.setText("Status: Checking for updates...");
            mCheckUpdateButton.setEnabled(false);
            mFirmwareDownloader.checkForFirmwareUpdate();
        } else {
            mStatusLineTextView.setText("Status: No internet connection available.");
        }
    }

    private void initializeFirmwareSpinner() {
        mSpinnerAdapter = new CustomSpinnerAdapter(BootloaderActivity.this, R.layout.spinner_rows, new ArrayList<Firmware>());
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFirmwareSpinner.setAdapter(mSpinnerAdapter);
        mFirmwareSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Firmware firmware = (Firmware) mFirmwareSpinner.getSelectedItem();
                if (firmware != null) {
                    mSelectedFirmware = firmware;
                    mFlashFirmwareButton.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mFlashFirmwareButton.setEnabled(false);
            }

        });
    }

    public void updateFirmwareSpinner(List<Firmware> firmwares) {
        mCheckUpdateButton.setEnabled(true);
        mSpinnerAdapter.clear();
        mSpinnerAdapter.addAll(firmwares);
    }

    public void setStatusLine (String status) {
        this.mStatusLineTextView.setText(status);
    }

    public void startFlashProcess(final View view) {
        //TODO: enable wakelock

        // disable buttons and spinner
        mCheckUpdateButton.setEnabled(false);
        mFlashFirmwareButton.setEnabled(false);
        mFirmwareSpinner.setEnabled(false);

        // download firmware file

        // TODO: not visible
        mStatusLineTextView.setText("Downloading firmware...");

        mFirmwareDownloader.addDownloadListener(new DownloadListener() {

            @Override
            public void downloadFinished() {
                //flash firmware once firmware is downloaded
                mStatusLineTextView.setText("Firmware downloaded.");
                flashFirmware(view);
            }
        });
        mFirmwareDownloader.downloadFirmware(this.mSelectedFirmware);
    }

    //TODO: simplify
    //TODO: mStatusLineTextView.setText("Status: Restart the Crazyflie you want to bootload in the next 10 seconds ...");
    public void flashFirmware(View view) {
        String result = "";
        try {
            mBootloader = new Bootloader(new RadioDriver(new UsbLinkAndroid(BootloaderActivity.this)));

            // start in async task?
            if (mBootloader.startBootloader(false)) {

                //TODO: externalize
                //Check if firmware is compatible with Crazyflie
                int protocolVersion = mBootloader.getProtocolVersion();
                boolean cfType2 = (protocolVersion == BootVersion.CF1_PROTO_VER_0 ||
                                    protocolVersion == BootVersion.CF1_PROTO_VER_1) ? false : true;

                String cfversion = "Found Crazyflie " + (cfType2 ? "2.0" : "1.0") + ".";
                mStatusLineTextView.setText(cfversion);
                Log.d(LOG_TAG, cfversion);

                if (("CF2".equalsIgnoreCase(mSelectedFirmware.getType()) && !cfType2) ||
                    ("CF1".equalsIgnoreCase(mSelectedFirmware.getType()) && cfType2)) {
                    mBootloader.resetToFirmware();
                    Log.d(LOG_TAG, "Incompatible firmware version.");
                    mStatusLineTextView.setText("Status: Incompatible firmware version.");
                    reenableWidgets();
                    return;
                }

                if (!mFirmwareDownloader.isFileAlreadyDownloaded(mSelectedFirmware.getTagName() + "/" + mSelectedFirmware.getAssetName())) {
                    mStatusLineTextView.setText("Status: Firmware file can not be found.");
                    reenableWidgets();
                    return;
                }

                //set progress bar max
                //TODO: progress bar max is reset when activity is resumed
                int pageSize = mBootloader.getTarget(TargetTypes.STM32).getPageSize();
                Log.d(LOG_TAG, "pageSize: " + pageSize);
                int firmwareSize = mSelectedFirmware.getAssetSize();
                Log.d(LOG_TAG, "firmwareSize: " + firmwareSize);
                int max = ((firmwareSize / pageSize) + 1);
                Log.d(LOG_TAG, "setMax: " + max);
                mProgressBar.setMax(max);

                Toast.makeText(this, "Flashing ...", Toast.LENGTH_SHORT).show();
                AsyncTask<String, String, String> task = new FlashFirmwareTask();
                task.execute();
                //TODO: wait for finished task
            } else {
                result = "Bootloader problem.";
            }
        } catch (IOException e) {
            result = "Bootloader problem: " + e.getMessage();
        } catch (IllegalArgumentException iae) {
            result = "Bootloader problem: " + iae.getMessage();
        }
        mStatusLineTextView.setText("Status: " + result);
    }

    private class FlashFirmwareTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {

            mBootloader.addBootloaderListener(new BootloaderListener() {

                @Override
                public void updateStatus(String status) {
                    publishProgress(new String[]{status, null, null});
                    Log.d(LOG_TAG, "Status: " + status);
                }

                @Override
                public void updateProgress(int progress) {
                    publishProgress(new String[]{null, "" + progress, null});
                    Log.d(LOG_TAG, "Progress: " + progress);
                }

                @Override
                public void updateError(String error) {
                    publishProgress(new String[]{null, null, error});
                    Log.d(LOG_TAG, "Error: " + error);
                }
            });

            File sdcard = Environment.getExternalStorageDirectory();
            File firmwareFile = new File(sdcard, FirmwareDownloader.DOWNLOAD_DIRECTORY + "/" + mSelectedFirmware.getTagName() + "/" + mSelectedFirmware.getAssetName());

            long startTime = System.currentTimeMillis();
            //TODO: fix for NRF51 files
            mBootloader.flash(firmwareFile, "stm32");
            String flashTime = "Flashing took " + (System.currentTimeMillis() - startTime)/1000 + " seconds.";
            Log.d(LOG_TAG, flashTime);
            return flashTime;
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            if (progress[0] != null) {
                mStatusLineTextView.setText("Status: " + progress[0]);
            } else if (progress[1] != null) {
                mProgressBar.setProgress(Integer.parseInt(progress[1]));
            } else if (progress[2] != null) {
                mStatusLineTextView.setText("Status: " + progress[2]);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            mBootloader.resetToFirmware();
            if (mBootloader != null) {
                mBootloader.close();
            }
            reenableWidgets();
            mProgressBar.setProgress(0);
        }
    }

    public void reenableWidgets() {
        mCheckUpdateButton.setEnabled(true);
        mFlashFirmwareButton.setEnabled(true);
        mFirmwareSpinner.setEnabled(true);
    }

    /**
     * @param context used to check the device version and DownloadManager information
     * @return true if the download manager is available
     */
    public static boolean isDownloadManagerAvailable(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return true;
        }
        return false;
    }
}
