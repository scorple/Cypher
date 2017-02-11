package com.semaphore_soft.apps.cypher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.semaphore_soft.apps.cypher.networking.ClientService;
import com.semaphore_soft.apps.cypher.networking.NetworkConstants;
import com.semaphore_soft.apps.cypher.networking.ResponseReceiver;
import com.semaphore_soft.apps.cypher.networking.Server;
import com.semaphore_soft.apps.cypher.networking.ServerService;
import com.semaphore_soft.apps.cypher.ui.PlayerID;
import com.semaphore_soft.apps.cypher.ui.PlayerIDAdapter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by Scorple on 1/9/2017.
 */

public class ConnectionLobbyActivity extends AppCompatActivity implements ResponseReceiver.Receiver
{
    String              name;
    boolean             host;
    long                playerID;
    ArrayList<PlayerID> playersList;

    private PlayerIDAdapter playerIDAdapter;

    RecyclerView recyclerView;
    private Intent           mServiceIntent;
    private ResponseReceiver responseReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_lobby);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        responseReceiver = new ResponseReceiver();
        responseReceiver.setListener(this);
        LocalBroadcastManager.getInstance(this)
                             .registerReceiver(responseReceiver,
                                               NetworkConstants.getFilter());


        host = getIntent().getBooleanExtra("host", false);

        TextView txtDisplayName = (TextView) findViewById(R.id.txtDisplayName);

        name = getIntent().getStringExtra("name");

        String welcomeText = "Welcome " + name;
        txtDisplayName.setText(welcomeText);

        recyclerView = (RecyclerView) findViewById(R.id.recPlayerCardList);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(llm);

        playersList = new ArrayList<>();
        addTestPlayers();

        playerIDAdapter = new PlayerIDAdapter(this, playersList);

        recyclerView.setAdapter(playerIDAdapter);

        Button btnStart = (Button) findViewById(R.id.btnStart);

        if (host)
        {
            playerID = 0;

            String ip = "";
            try
            {
                // Use a label to break out of a nested for loop
                // Note: probably better to use a method for the inner loop, but this works
                outerloop:
                for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
                {
                    NetworkInterface ni = en.nextElement();
                    for (Enumeration<InetAddress> addresses = ni.getInetAddresses(); addresses.hasMoreElements();)
                    {
                        InetAddress inetAddress = addresses.nextElement();
                        // Limit IP addresses shown to IPv4
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
                        {
                            ip = inetAddress.getHostAddress();
                            Log.i("Lobby", ip);
                            break outerloop;
                        }
                    }
                }
            }
            catch (SocketException ex)
            {
                Log.e("Lobby", ex.toString());
            }

            mServiceIntent = new Intent(this, ServerService.class);
            mServiceIntent.setData(Uri.parse(NetworkConstants.WRITE_TO_CLIENT));
            mServiceIntent.putExtra(NetworkConstants.MSG_EXTRA, "Hello, World!");
            mServiceIntent.putExtra(NetworkConstants.INDEX_EXTRA, 0);
            startService(mServiceIntent);

            TextView ipAddress = (TextView) findViewById(R.id.ip_address);
            ipAddress.setText("Your IP Address is: " + ip);

            btnStart.setEnabled(true);

            btnStart.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    Server.setAccepting(false);
                    mServiceIntent.setData(Uri.parse(NetworkConstants.WRITE_ALL));
                    mServiceIntent.putExtra(NetworkConstants.MSG_EXTRA,
                                            NetworkConstants.GAME_START);
                    startService(mServiceIntent);
                    LocalBroadcastManager.getInstance(ConnectionLobbyActivity.this).unregisterReceiver(responseReceiver);
                    Snackbar.make(view, "Moving to Character Select", Snackbar.LENGTH_LONG).show();
                    Intent intent = new Intent(getBaseContext(), CharacterSelectActivity.class);
                    intent.putExtra("host", host);
                    intent.putExtra("player", playerID);
                    startActivity(intent);
                }
            });
        }
        else
        {
            btnStart.setEnabled(false);

            TextView ipAddress = (TextView) findViewById(R.id.ip_address);
            ipAddress.setVisibility(View.GONE);

            mServiceIntent = new Intent(this, ClientService.class);
            mServiceIntent.setData(Uri.parse(NetworkConstants.CLIENT_WRITE));
            mServiceIntent.putExtra(NetworkConstants.MSG_EXTRA, NetworkConstants.PF_NAME + name);
            startService(mServiceIntent);
        }
    }

    private void addTestPlayers()
    {
        for (int i = 0; i < 3; ++i)
        {
            PlayerID playerID = new PlayerID();
            playerID.setID(i);
            playerID.setPlayerName("player" + i);
            //gameIDAdapter.pushGameID(gameID);
            playersList.add(playerID);
        }
    }

    private void addPlayers(String player, int id)
    {
        PlayerID playerID = new PlayerID();
        playerID.setID(id);
        playerID.setPlayerName(player);
        playersList.add(playerID);
        playerIDAdapter.notifyDataSetChanged();
    }

    @Override
    public void handleRead(String msg)
    {
        Toast.makeText(this, "Read: " + msg, Toast.LENGTH_SHORT).show();
        if (msg.equals(NetworkConstants.GAME_START))
        {
            // Start character select activity after host has started game
            Intent intent = new Intent(getBaseContext(), CharacterSelectActivity.class);
            intent.putExtra("host", host);
            intent.putExtra("player", playerID);
            startActivity(intent);
        }
        else if (msg.startsWith(NetworkConstants.PF_NAME))
        {
            addPlayers(msg.substring(5), 0);
        }
    }

    @Override
    public void handleStatus(String msg)
    {
        Toast.makeText(this, "Status: " + msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void handleError(String msg)
    {
        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show();
    }
}
