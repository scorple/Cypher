package com.semaphore_soft.apps.cypher;

import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

/**
 * Created by Evan on 1/31/2017.
 * Dialog to get host address information
 */

public class ConnectFragment extends DialogFragment
{
    private EditText name;
    private EditText addr;

    private callback myListener;

    public ConnectFragment()
    {

    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View myView = inflater.inflate(R.layout.connect, container, false);

        getDialog().setTitle("Enter connection info");

        name = (EditText) myView.findViewById(R.id.playerName);

        addr = (EditText) myView.findViewById(R.id.hostAddr);

        Button cancel = (Button) myView.findViewById(R.id.cancel);
        cancel.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                dismiss();
            }
        });

        Button connect = (Button) myView.findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                myListener.doNetwork(addr.getText().toString(), name.getText().toString());

                // hide keyboard on fragment exit
                InputMethodManager imm = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                boolean b = imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);

                dismiss();
            }
        });

        return myView;
    }

    public interface callback {
        void doNetwork(String addr, String name);
    }

    public void setListener(callback c)
    {
        myListener = c;
    }
}
