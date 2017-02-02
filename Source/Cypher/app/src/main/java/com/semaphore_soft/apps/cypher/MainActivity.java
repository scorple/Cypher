package com.semaphore_soft.apps.cypher;


import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.semaphore_soft.apps.cypher.ui.GetNameDialogFragment;

public class MainActivity extends AppCompatActivity implements GetNameDialogFragment.GetNameDialogListener, ConnectFragment.callback
{
    boolean host = false;

    private final static String TAG = "Main";
    // Port should be between 49152-65535
    public final static int SERVER_PORT = 58008;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Toast.makeText(getApplicationContext(), "Launching AR Activity", Toast.LENGTH_SHORT)
                        .show();

                Intent intent = new Intent(getBaseContext(), PortalActivity.class);
                intent.putExtra("host", true);
                intent.putExtra("player", 0);
                intent.putExtra("character", 0);
                startActivity(intent);
            }
        });

        // Allow network connections
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitNetwork().build();
        StrictMode.setThreadPolicy(policy);

        Button btnHost = (Button) findViewById(R.id.btnHost);

        btnHost.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                host = true;
                showGetNameDialog();
            }
        });

        Button btnJoin = (Button) findViewById(R.id.btnJoin);
        btnJoin.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                host = false;
                showConnectDialog();
            }
        });
    }

    public void showGetNameDialog()
    {
        FragmentManager       fm                    = getSupportFragmentManager();
        GetNameDialogFragment getNameDialogFragment = new GetNameDialogFragment();
        getNameDialogFragment.setListener(this);
        getNameDialogFragment.show(fm, "get_name_dialog");
    }

    public void showConnectDialog()
    {
        FragmentManager fm = getSupportFragmentManager();
        ConnectFragment connectFragment = new ConnectFragment();
        connectFragment.setListener(this);
        connectFragment.show(fm, "connect_dialog");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void doNetwork(String addr, String name)
    {
        Toast.makeText(getApplicationContext(), "Moving to Connection Lobby",
                Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getBaseContext(), ConnectionLobbyActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("address", addr);
        startActivity(intent);
    }

    @Override
    public void onFinishGetName(String name)
    {
        Toast.makeText(getApplicationContext(), "Moving to Connection Lobby",
                Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getBaseContext(), ConnectionLobbyActivity.class);
        intent.putExtra("host", host);
        intent.putExtra("name", name);
        startActivity(intent);
    }
}
