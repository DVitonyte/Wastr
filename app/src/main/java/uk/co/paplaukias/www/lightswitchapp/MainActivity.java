package uk.co.paplaukias.www.lightswitchapp;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity{
    BluetoothGattCharacteristic lightSwitchWriteCharacteristic;
    BluetoothGattCharacteristic lightSwitchReadCharacteristic;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;

    private Handler mHandler;
    private boolean mConnected;
    private boolean mMenuCreated;
    boolean mScanning;
    boolean lightsOn;

    private static final int SCAN_PERIOD = 5000;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int CONNECT_STATE = 0;
    private static final int SCANNING_STATE = 1;
    private static final int DISCONNECT_STATE = 2;

    private static final String LIGHT_CONTROLLER_ADDRESS = "AA:AA:AA:0B:00:B5";
    private static final String TAG = "BluetoothActivity";

    private ImageButton mSwitch1;
    //private Switch mSwitch2;
    private MenuItem connectButton;
    Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //the device is not scanning for light controller and is not to it
        mScanning = false;
        mConnected = false;
        mMenuCreated = false;

        //initialise switches for light control
        mSwitch1 = (ImageButton) findViewById(R.id.imageButton);
        //mSwitch2 = (Switch) findViewById(R.id.switch2);

        //if device is not connected to the light controller then disable the interface
        setInterfaceEnabled(false);

        //initialise the handler for time-outs and the bluetooth adapter
        mHandler = new Handler();

        //TODO: might need to move the check to onStart() method
        //Check whether the device has BLE
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, "No BLE Support", Toast.LENGTH_SHORT).show();
        } else {
            BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = manager.getAdapter();
        }
    }

    /*
    Creates the action bar menu
    Executed only once, when the app is created, thus mConnected will always be "false"
    Creates the menu and adds the "Connect" button to the menu
    Set the button title to "Connect"
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu Entered");
        //Add the button to the action bar
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        connectButton = menu.findItem(R.id.scan);
        //set the button title to "Connect"
        updateMenuButton(CONNECT_STATE);
        mMenuCreated = true;
        scanLeDevice(true);
        return true;
    }

    /*
    Checks that the bluetooth module is turned on
    If it's turned off, prompts the user to turn it on
     */
    @Override
    protected void onStart() {
        super.onStart();
        //Check if bluetooth is enabled
        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            //Bluetooth is disabled. Prompt to enable bluetooth on the device
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /*
    Executed when the application becomes visible again
    Runs this method after the user presses a button on the bluetooth prompt
    If the user presses ALLOW, turn on the bluetooth
    If the user presses DENY, cancel the intent and exit the application
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult Entered");
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                // User pressed DENY, exit the application
                Toast.makeText(this, "App cannot run with bluetooth off", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data);
    }

    /*
    Executed after onStart()
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume Entered");
        if(mMenuCreated) scanLeDevice(true);
    }

    /*
    Executed after onResume() when another activity comes to the foreground
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause Entered");
        if(mMenuCreated) scanLeDevice(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop Entered");
        close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "onOptionsItemsSelected Entered");
        switch(item.getItemId()){
            case R.id.scan:
                if(mConnected){
                    close();
                    scanLeDevice(false);
                    mConnected = false;
                    return true;
                }else{
                    close();
                    scanLeDevice(true);
                    //Toast.makeText(this, "Scanning for Light Controller", Toast.LENGTH_SHORT).show();
                    return true;
                }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*
    Starts or stops BLE device scanning
    If a "true" argument is passed, the device starts scanning for BLE devices
    If a "false" argument is passes, the device stops scanning for BLE devices
     */
    private void scanLeDevice(final boolean enable) {
        Log.i(TAG, "scanLeDevice Entered");
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //updateMenuButton(CONNECT_STATE);
                    mBluetoothAdapter.stopLeScan(mScanCallback);
                    //Toast.makeText(MainActivity.this, "Could not find the Light Controller", Toast.LENGTH_SHORT).show();
                }
            }, SCAN_PERIOD);
            // Starts scanning, attaches the mScanCallback method
            updateMenuButton(SCANNING_STATE);
            mBluetoothAdapter.startLeScan(mScanCallback);
        } else {
            // Stops scanning, detaches the mScanCallback method
            setInterfaceEnabled(false);
            updateMenuButton(CONNECT_STATE);
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
    }

    /*
    BLE scan callback
    Gets called when a BLE device is found
    Checks if the device address is the same as the LIGHT_CONTROLLER_ADDRESS
    If true, then connects to the light controller
     */
    private BluetoothAdapter.LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int i, final byte[] bytes) {
            // Check if the BLE device is the light controller
            String deviceAddress = bluetoothDevice.getAddress();
            if(deviceAddress.equals(LIGHT_CONTROLLER_ADDRESS)) {
                // Connect to the light controller, attach the mGattCallback
                mGatt = bluetoothDevice.connectGatt(MainActivity.this, false, mGattCallback);
            }
        }
    };

    /*
    Gets called when the BLE device connection status changes (when the devices connects/disconnects
    from the light controller
    It runs in the background, thus if need to make changes to the UI, use runOnUiThread()
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Connected to the light controller
                Log.i(TAG, "Connected to GATT server.");
                mConnected = true;
                if (mGatt.discoverServices()) {
                    Log.d(TAG, "Started service discovery.");
                } else {
                    Log.w(TAG, "Service discovery failed.");
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Disconnected from the light controller
                Log.i(TAG, "Disconnected from GATT server.");
                mConnected = false;
            }

            // Update the UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(mConnected){
                        updateMenuButton(DISCONNECT_STATE);
                        Toast.makeText(MainActivity.this, "Connected to the Light Controller", Toast.LENGTH_SHORT).show();
                    }else{
                        updateMenuButton(CONNECT_STATE);
                        Toast.makeText(MainActivity.this, "Disconnected from the Light Controller", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BluetoothGattService lightControlService = gatt.getService(AssignedNumber.getBleUuid("Light Control"));
            if (lightControlService != null) {
                // Light control services were discovered
                lightSwitchWriteCharacteristic = lightControlService.getCharacteristic(AssignedNumber.getBleUuid("Light Switches Write"));
                if (lightSwitchWriteCharacteristic != null) {
                    Log.i(TAG, "Found light switch characteristic.");
                } else {
                    Log.w(TAG, "Can't find light switch write characteristic.");
                }
                lightSwitchReadCharacteristic = lightControlService.getCharacteristic(AssignedNumber.getBleUuid("Light Switches Read"));
                if (lightSwitchReadCharacteristic != null) {
                    Log.i(TAG, "Found light switch characteristic.");
                    mGatt.setCharacteristicNotification(lightSwitchReadCharacteristic, true);
                    BluetoothGattDescriptor descriptor = lightSwitchReadCharacteristic.getDescriptor(AssignedNumber.getBleUuid("Client Characteristic Configuration"));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mGatt.writeDescriptor(descriptor);
                } else {
                    Log.w(TAG, "Can't find light switch read characteristic.");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setInterfaceEnabled(true);
                    }
                });

            } else {
                // Light control services were not discovered
                Log.w(TAG, "Can't find light control service.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setInterfaceEnabled(false);
                        Toast.makeText(MainActivity.this, "Can't find Light Controller services", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG, "onCharacteristicRead Entered");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (characteristic.getUuid().equals(AssignedNumber.getBleUuid("Light Switches Read"))) {
                final int lightSwitchState = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        switch (lightSwitchState){
                            case 0:
                                mSwitch1.setImageResource(R.drawable.on);
                                lightsOn = false;
                                //mSwitch2.setChecked(false);
                                break;

                            case 1:
                                mSwitch1.setImageResource(R.drawable.off);
                                lightsOn = true;
                                //mSwitch2.setChecked(false);
                                break;

                            case 2:
                                mSwitch1.setImageResource(R.drawable.on);
                                lightsOn = false;
                                //mSwitch2.setChecked(true);
                                break;

                            case 3:
                                mSwitch1.setImageResource(R.drawable.off);
                                lightsOn = true;
                                //mSwitch2.setChecked(true);
                                break;

                            default:
                                mSwitch1.setImageResource(R.drawable.on);
                                lightsOn = false;
                                //mSwitch2.setChecked(false);
                                break;
                        }
                    }
                });
            }
        }

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG, "Successfully written.");
            } else {
                Log.w(TAG, "Write failed with code " + status + ".");
            }
        }
    };

    public void onSwitch1Click(View view) {
        lightSwitchWriteCharacteristic.setValue(new byte[] { 1 });
        if (!mGatt.writeCharacteristic(lightSwitchWriteCharacteristic)) {
            Log.w(TAG, "Failed to write to music characteristic.");
        }
        if(!lightsOn){
            mSwitch1.setImageResource(R.drawable.off);
            lightsOn = true;
        } else{
            mSwitch1.setImageResource(R.drawable.on);
            lightsOn = false;
        }
    }
    /*
    public void onSwitch2Click(View view) {
        lightSwitchWriteCharacteristic.setValue(new byte[] { 2 });
        if (!mGatt.writeCharacteristic(lightSwitchWriteCharacteristic)) {
            Log.w(TAG, "Failed to write to music characteristic.");
        }
    }
    */

    /*
    Enable/disable the app interface
     */
    private void setInterfaceEnabled(boolean enabled) {
        if(!enabled) mSwitch1.setImageResource(R.drawable.disabled);
        else mSwitch1.setImageResource(R.drawable.on);
        mSwitch1.setEnabled(enabled);
        //mSwitch2.setEnabled(enabled);
    }

    /*
    Terminate the GATT connection with the light controller
     */
    public void close() {
        if (mGatt == null) return;
        mGatt.disconnect();
        mGatt.close();
        mGatt = null;
    }

    public void updateMenuButton(int connectionState){
        switch(connectionState){
            case CONNECT_STATE:
                connectButton.setTitle(R.string.connect);
                connectButton.setEnabled(true);
                mScanning = false;
                break;

            case SCANNING_STATE:
                connectButton.setTitle(R.string.scanning);
                connectButton.setEnabled(false);
                mScanning = true;
                break;

            case DISCONNECT_STATE:
                connectButton.setTitle(R.string.disconnect);
                connectButton.setEnabled(true);
                mScanning = false;
                break;
        }
    }
}
