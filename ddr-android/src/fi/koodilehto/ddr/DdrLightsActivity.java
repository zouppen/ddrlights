package fi.koodilehto.ddr;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.android.future.usb.*;
import java.io.*;
import java.util.*;

public class DdrLightsActivity extends Activity implements SensorEventListener {

	// TAG is used to debug in Android logcat console
	public static final String TAG = "DDRLights";

	private static final String ACTION_USB_PERMISSION = "fi.koodilehto.ddr.action.USB_PERMISSION";

	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	private Lights lights = new Lights(6);
	private int currentView;
	private double[] earthAcc = { 0, 0, 0 };

	private int hoffLocation = 0;
	private int hoffMax = 1000;
	private SeekBar hoffSpeed;

	private Timer hoffTimer;
	private TimerTask hoffTask;

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}

		// Accelerometer
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// Moved from onResume to allow background processing.
		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}

		// Activate default page
		goToSelector();
	}

	private void jumpTo(int id) {
		currentView = id;
		setContentView(currentView);
	}

	// TODO It's now ugly as the hell.
	private void goToSelector() {
		disableHoffTimer();
		disableAccel();

		currentView = R.layout.selector;
		setContentView(R.layout.selector);
		ListView tasks = (ListView) findViewById(R.id.taskselector);
		// Ugly but works for now
		tasks.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int pos,
					long id) {
				switch (pos) {
				case 0:
					jumpTo(R.layout.toggle);
					break;
				case 1:
					SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {

						@Override
						public void onProgressChanged(SeekBar seekBar,
								int progress, boolean fromUser) {
							// Making it more natural by logarithm
							double natural = Math.pow(progress, 2)
									/ Math.pow(seekBar.getMax(), 2);

							byte lightID = Byte.parseByte((String) seekBar
									.getTag());
							lights.set(lightID, natural);
							try {
								lights.refresh();
							} catch (IOException e) {
								// Too tired to process it.
							}
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}

					};

					jumpTo(R.layout.sliders);
					((SeekBar) findViewById(R.id.intensityBar0))
							.setOnSeekBarChangeListener(listener);
					((SeekBar) findViewById(R.id.intensityBar1))
							.setOnSeekBarChangeListener(listener);
					((SeekBar) findViewById(R.id.intensityBar2))
							.setOnSeekBarChangeListener(listener);
					((SeekBar) findViewById(R.id.intensityBar3))
							.setOnSeekBarChangeListener(listener);
					((SeekBar) findViewById(R.id.intensityBar4))
							.setOnSeekBarChangeListener(listener);
					((SeekBar) findViewById(R.id.intensityBar5))
							.setOnSeekBarChangeListener(listener);
					break;
				case 2:
					jumpTo(R.layout.accel);
					break;
				case 3:
					jumpTo(R.layout.hoff);
					hoffSpeed = (SeekBar) findViewById(R.id.hoffspeed);
					break;
				case 5:
					jumpTo(R.layout.credits);
					break;
				default:
					// Jump nowhere.
				}

				if (pos == 2)
					enableAccel();
				else
					disableAccel();

				if (pos == 3)
					enableHoffTimer();
				else
					disableHoffTimer();
			}
		});
		currentView = R.layout.selector;
	}

	@Override
	public void onBackPressed() {
		if (currentView == R.layout.selector) {
			super.onBackPressed();
		} else {
			goToSelector();
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		/*
		 * if (currentView == R.layout.accel) { enableAccel(); } else if
		 * (currentView == R.layout.hoff) { enableHoffTimer(); }
		 */
	}

	@Override
	public void onPause() {
		super.onPause();
		// lights.destroyStream();
		// closeAccessory();
		// disableAccel();
		// disableHoffTimer();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
	}

	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			lights.changeStream(new FileOutputStream(fd));
			Log.d(TAG, "accessory opened");
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}

	private void closeAccessory() {
		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}

	public void blinkLED(View v) {

		byte ledID = Byte.parseByte((String) v.getTag());
		ToggleButton buttonLED = (ToggleButton) v;

		boolean state = buttonLED.isChecked();

		lights.set(ledID, state);

		try {
			lights.refresh();
		} catch (IOException e) {
			Log.e(TAG, "write failed", e);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// Ignored on purpose.
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		final double weight = 0.05;
		final double[] inZeroG = new double[3];
		double zeroAcc = 0;

		for (int i = 0; i < 3; i++) {
			// Removing the effect of Earth's gravity from current values
			inZeroG[i] = event.values[i] - earthAcc[i];
			zeroAcc += Math.pow(inZeroG[i], 2);

			// Updating Earth direction
			earthAcc[i] = (1 - weight) * earthAcc[i] + weight * event.values[i];
		}
		zeroAcc = Math.sqrt(zeroAcc);
		Log.d(TAG, "Acc: " + zeroAcc);

		for (int i = 0; i < lights.count(); i++) {
			lights.set(i, Math.pow(zeroAcc, 2) / 100);
		}

		try {
			lights.refresh();
		} catch (IOException e) {
			// Not interested of this.
		}
	}

	private void enableAccel() {
		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	private void disableAccel() {
		mSensorManager.unregisterListener(this);
	}

	private void disableHoffTimer() {
		if (this.hoffTimer == null)
			return;
		this.hoffTimer.cancel();
		this.hoffTimer = null;
		this.hoffTask = null;
	}

	private void enableHoffTimer() {
		if (this.hoffTimer != null)
			return;
		this.hoffTask = new HoffTask();
		this.hoffTimer = new Timer(true);
		this.hoffTimer.schedule(hoffTask, 0, 20);
	}

	private class HoffTask extends TimerTask {

		@Override
		public void run() {
			hoffLocation += hoffSpeed.getProgress() - (hoffSpeed.getMax() / 2);
			hoffLocation = hoffLocation % hoffMax;
			double ledCoord = (double) hoffLocation / hoffMax * lights.count();
			final double rollPoint = (double) lights.count() / 2;

			for (int i = 0; i < lights.count(); i++) {
				// Some modulo algebra
				double rel = ledCoord - i;
				if (rel < -rollPoint)
					rel += lights.count();
				if (rel > rollPoint)
					rel -= lights.count();

				double intensity = 1 - (0.5 * rel * rel);
				lights.set(i, intensity);
			}
			try {
				lights.refresh();
			} catch (IOException e) {
				// Uninteresting. Shit may happen.
			}
		}
	}

}
