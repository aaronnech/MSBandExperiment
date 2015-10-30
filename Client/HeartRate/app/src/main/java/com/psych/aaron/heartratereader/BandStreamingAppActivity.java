package com.psych.aaron.heartratereader;

import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.SampleRate;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.app.Activity;
import android.os.AsyncTask;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * @Author: Aaron Nech
 * Streams multiple connected MS Band datas to a central server for post processing
 */
public class BandStreamingAppActivity extends Activity {
	// Current client pointer
	private BandClient currentClient = null;

	// List of all band clients
	private List<BandClient> clients = null;

	// Session ID generated for a particular stream
	private String sessionID = null;

	// UI stuff
	private Button btnStart;
	private TextView txtStatus;
	private TextView bandStatus;
	private Queue<Packet> buffer;
	private int amountSent;
	private boolean started;

	// A map from BandClient objects to BandInfo objects (which contains the band name)
	private Map<BandClient, BandInfo> nameMap;

	// A timer for sending payloads to the server
	private Timer serverTimer;

	/**
	 * Aysnc task started when collection begins. The first thing we do is check for any
	 * connected band clients, and get consent for their sensors. We then periodically ship
	 * a buffer
	 */
	private class startTask extends AsyncTask<Void, Void, Void> implements HeartRateConsentListener {
		@Override
		protected Void doInBackground(Void... params) {
			try {
				// If we get a connected band, grab consent
				if (getConnectedBandClient()) {
					appendToUI("Band is connected. Getting consent for sensors..\n");
					for (BandClient client : clients) {
						currentClient = client;
						if(client.getSensorManager().getCurrentHeartRateConsent() !=
								UserConsent.GRANTED) {
							appendToUI("Not yet authenticated.. Asking..\n");
							client.getSensorManager().requestHeartRateConsent(BandStreamingAppActivity.this, this);
						} else {
							appendToUI("Consent granted!");
							this.userAccepted(true);
						}
					}

					BandStreamingAppActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							shipBuffer();
						}
					});
				} else {
					appendToUI("No bands connected. Try again.");
				}
			} catch (BandException e) {
				String exceptionMessage="";
				switch (e.getErrorType()) {
					case UNSUPPORTED_SDK_VERSION_ERROR:
						exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
						break;
					case SERVICE_ERROR:
						exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
						break;
					default:
						exceptionMessage = "Unknown error occured: " + e.getMessage();
						break;
				}
				appendToUI(exceptionMessage);
			} catch (Exception e) {
				appendToUI(e.getMessage());
			}
			return null;
		}

		/**
		 * Called when a band gives us consent to harvest data
		 * @param consentGiven true if the consent is given, false otherwise
		 */
		@Override
		public void userAccepted(boolean consentGiven) {
			try {
				// We now need to attach various event listeners for different data from the band
				// And do a callback to this class in the ugliest way possible since Java sucks

				// Heart rate callback
				BandHeartRateEventListener heartRateListener = new BandHeartRateEventListener() {
					public BandClient band = currentClient;

					@Override
					public void onBandHeartRateChanged(BandHeartRateEvent event) {
						BandStreamingAppActivity.this.onBandHeartRate(event, band);
					}
				};

				// Accelerometer callback
				BandAccelerometerEventListener accelerometerEventListener = new BandAccelerometerEventListener() {
					public BandClient band = currentClient;

					@Override
					public void onBandAccelerometerChanged(BandAccelerometerEvent event) {
						BandStreamingAppActivity.this.onBandAccelerometer(event, band);
					}
				};

				// Register callbacks
				currentClient.getSensorManager().registerHeartRateEventListener(heartRateListener);
				currentClient.getSensorManager().registerAccelerometerEventListener(
						accelerometerEventListener, SampleRate.MS128);

			} catch (Exception e) {
				appendToUI(e.getMessage());
			}
		}
	}

	/**
	 * Buffers a single packet to ship to the server
	 * @param packet
	 */
	private void bufferPacket(Packet packet) {
		if (!started) {
			return;
		}
		System.out.println(packet);
		buffer.add(packet);
	}

	/**
	 * Initializes the Activity
	 * @param savedInstanceState The saved state
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

		buffer = new LinkedList<Packet>();
		clients = new LinkedList<BandClient>();
		nameMap = new HashMap<BandClient, BandInfo>();
		serverTimer = null;
		started = false;
        txtStatus = (TextView) findViewById(R.id.txtStatus);
		bandStatus = (TextView) findViewById(R.id.bandStatus);
        btnStart = (Button) findViewById(R.id.btnStart);

        btnStart.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (started) {
					endStreaming();
				} else {
					sessionID =  UUID.randomUUID().toString();
					beginStreaming();
				}
			}
		});
    }

	/**
	 * Starts streaming MS Band data
	 * @return true if started successfully, false otherwise
	 */
	private boolean beginStreaming() {
		started = true;
		amountSent = 0;
		txtStatus.setText("");
		btnStart.setText("Stop");

		// Start connection and harvest process
		new startTask().execute();

		return true;
	}

	/**
	 * Stops streaming MS Band data
	 * @return true if stopped successfully, false otherwise
	 */
	private boolean endStreaming() {
		started = false;
		btnStart.setText("Start");
		txtStatus.setText("Ready to start.");

		// Remove server timer (stop sending data!)
		if (serverTimer != null) {
			serverTimer.cancel();
			serverTimer.purge();
			serverTimer = null;
		}

		// Unregister listeners
		for (BandClient client : clients) {
			try {
				client.getSensorManager().unregisterAccelerometerEventListeners();
				client.getSensorManager().unregisterHeartRateEventListeners();
			} catch (Exception e) {
				appendToUI(e.getMessage());
			}
		}

		return true;
	}

	/**
	 * Appends a String to the UI
	 * @param string
	 */
	private void appendToUI(final String string) {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtStatus.setText(string);
			}
		});
	}

	/**
	 * Appends a String to the Band UI
	 * @param string
	 */
	private void appendToBandUI(final String string) {
		this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				bandStatus.setText(string);
			}
		});
	}

	/**
	 * Gets a connected band
	 * @return True when the bands are connected, false otherwise
	 * @throws InterruptedException
	 * @throws BandException
	 */
	private boolean getConnectedBandClient() throws InterruptedException, BandException {
		for (BandClient client : clients) {
			client.disconnect();
		}

		clients.clear();
		nameMap.clear();


		BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
		if (devices.length == 0) {
			appendToBandUI("No bands are paired with your phone.");
			return false;
		} else {
			appendToBandUI(devices.length + " bands are paired with your phone.");
		}

		for (int i = 0; i < devices.length; i++) {
			BandClient client = BandClientManager.getInstance().create(getBaseContext(), devices[i]);

			appendToBandUI("Waiting for band " + i);
			if (ConnectionState.CONNECTED == client.connect().await()) {
				clients.add(client);
				nameMap.put(client, devices[i]);
			}
		}

		appendToBandUI(clients.size() + " bands are connected to the application.");
		return true;
	}

	/**
	 * Called when we get accelerometer data
	 * @param event
	 * @param band
	 */
	private void onBandAccelerometer(final BandAccelerometerEvent event, BandClient band) {
		if (event != null && started) {
			Packet packet = new Packet();
			packet.data = "" +
					event.getAccelerationX() +
					"," +
					event.getAccelerationY() +
					"," +
					event.getAccelerationZ();
			packet.bandID = nameMap.get(band).getName();
			packet.timeStamp = System.currentTimeMillis();
			packet.type = "Accelerometer";

			BandStreamingAppActivity.this.bufferPacket(packet);
		}
	}

	/**
	 * Called when we get heart rate data
	 * @param event
	 * @param band
	 */
	private void onBandHeartRate(BandHeartRateEvent event, BandClient band) {
		if (event != null && started) {
			Packet packet = new Packet();
			packet.data = "" + event.getHeartRate();
			packet.bandID = nameMap.get(band).getName();
			packet.timeStamp = System.currentTimeMillis();
			packet.type = "HeartRate";

			bufferPacket(packet);
		}
	}

	/**
	 * Ships the current buffer of packets to the server
	 */
	private void shipBuffer() {
		final Handler handler = new Handler();
		if (serverTimer == null) {
			serverTimer = new Timer();
		}

		TimerTask doAsynchronousTask = new TimerTask() {
			@Override
			public void run() {
				handler.post(new Runnable() {
					public void run() {
						if (!buffer.isEmpty() && started) {
							appendToUI("Sending data bundle " + amountSent + "...");

							try {
								ServerShipTask ship = new ServerShipTask(sessionID);

								List<Packet> list = new ArrayList(buffer);
								buffer.clear();

								ship.execute(list);
							} catch (Exception e) {
								appendToUI(e.getMessage());
							}

							amountSent++;
						}
					}
				});
			}
		};

		amountSent++;

		if (started) {
			serverTimer.schedule(doAsynchronousTask, 0, 2000);
		} else {
			doAsynchronousTask.cancel();
		}
	}
}

