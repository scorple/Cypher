package com.semaphore_soft.apps.cypher;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Evan on 9/24/2016.
 * Broadcast Receiver for Wifi P2P connections
 */

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private MainActivity mActivity;
    private List<WifiP2pDevice> peers = new ArrayList<>();

    private final static String TAG = "WifiBR";

    public WiFiDirectBroadcastReceiver (WifiP2pManager manager, WifiP2pManager.Channel channel,
                                        MainActivity activity) {
        super();
        this.mManager = manager;
        this.mChannel = channel;
        this.mActivity = activity;
    }

    private WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            // Clear old list and add in new peers
            peers.clear();
            peers.addAll(peerList.getDeviceList());

            if (peers.size() == 0) {
                Log.d(TAG, "No devices found");
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("No hosts found");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
                AlertDialog alert = builder.create();
                mActivity.getPeerProgress().dismiss();
                alert.show();
                return;
            }

            Log.d(TAG, "Peers found: " + peers.size());
            // Create list of names for user to choose
            String[] deviceNames = new String[peers.size()];
            int i = 0;
            for (WifiP2pDevice device : peers) {
                Log.i(TAG, device.toString());
                deviceNames[i] = device.deviceName;
                i++;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle("Choose Host");
            builder.setSingleChoiceItems(deviceNames, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                    Log.d(TAG, "Connecting to: " + peers.get(i).deviceName + " at " + peers.get(i).deviceAddress);
                    Toast.makeText(mActivity, "Connecting to: " + peers.get(i).deviceName,
                            Toast.LENGTH_SHORT).show();
                    connect(peers.get(i));
                }
            });
            AlertDialog alert = builder.create();
            mActivity.getPeerProgress().dismiss();
            alert.show();
        }
    };

    private WifiP2pManager.ConnectionInfoListener connectionListener =
            new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            // InetAddress from WifiP2pInfo struct
            InetAddress groupOwnerAddress;
            try {
                groupOwnerAddress = InetAddress.getByName(wifiP2pInfo.groupOwnerAddress.getHostAddress());
            } catch (UnknownHostException e) {
                Log.e(TAG, "Host IP: " + wifiP2pInfo.groupOwnerAddress.getHostAddress());
                e.printStackTrace();
            }

            // After group negotiation, we can determine the group owner
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                // Tasks specific to group owner
                // ex. Create server thread and listen for incoming connections
                Log.d(TAG, "Device is group owner");
                Toast.makeText(mActivity, "You're the group owner!", Toast.LENGTH_SHORT).show();
                //port: 49152-65535
            } else if (wifiP2pInfo.groupFormed) {
                // Device acts as client
                // Create client thread that connects to group owner
                Log.d(TAG, "Device is in group");
                Toast.makeText(mActivity, "You're in a group!", Toast.LENGTH_SHORT).show();
            }
        }
    };

    public void connect(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Broadcast Receiver will notify us.
                Log.d(TAG, "Connect success");
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Connect failed. Reason " + i);
                Toast.makeText(mActivity, "Connect failed. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onReceive (Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi P2P is enabled
                Log.d(TAG, "P2P enabled");
            } else {
                // Wifi P2P is not enabled
                Log.d(TAG, "P2P disabled");
                // Check if wifi is disabled and enable it if so
                WifiManager wifi = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE);
                if (!wifi.isWifiEnabled()) {
                    wifi.setWifiEnabled(true);
                    Toast.makeText(mActivity, "Enabled wifi", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Enabled wifi");
                    return;
                }

                // Display an error if wifi P2P is not supported
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("Wifi P2P disabled");
                builder.setMessage("This device does not support Wifi P2P.\nThe application will now exit.");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Close dialog first (avoids error on close), then exit activity
                        dialogInterface.dismiss();
                        mActivity.finish();
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // Request available peers. This is an asynchronous call. The calling activity
            // is notified with a callback on PeerListListener.onPeerAvailable()
            if (mManager != null) {
                mManager.requestPeers(mChannel, peerListListener);
            }
            Log.d(TAG, "Peer list changed");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            Log.d(TAG, "Connection changed");

            if (mManager == null) {
                return;
            }

            NetworkInfo networkInfo = intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                // We are connected with another device, request connection
                // info to find group owner IP
                Log.d(TAG, "Connected");
                Toast.makeText(mActivity, "Connected!", Toast.LENGTH_SHORT).show();
                mManager.requestConnectionInfo(mChannel, connectionListener);
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing
            Log.d(TAG, "This device's state changed");
        }
    }
}
