package com.semaphore_soft.apps.cypher.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.semaphore_soft.apps.cypher.R;

/**
 * Created by Crud Stuntley on 2/5/2017.
 */

public class UIMainActivity extends UIBase
{
    public UIMainActivity(Context context)
    {
        super(context);
    }

    public UIMainActivity(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public UIMainActivity(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void createUI()
    {
        //all buttons live here
        //makeView with layout corresponding to this ui
        //define on click
        //call notifyListener method
        makeView(R.layout.activity_main);

        //Host Button
        ImageButton btnHost = (ImageButton) findViewById(R.id.btnHost);
        btnHost.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                notifyListener("cmd_btnHost");
            }
        });

        //Join Button
        ImageButton btnJoin = (ImageButton) findViewById(R.id.btnJoin);
        btnJoin.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                notifyListener("cmd_btnJoin");
            }
        });

        //Options Button
        ImageButton btnOptions = (ImageButton) findViewById(R.id.btnOptions);
        btnOptions.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                notifyListener("cmd_btnOptions");
            }
        });

        //Launch Button
        /*Button btnLaunch = (Button) findViewById(R.id.btnLaunch);
        btnLaunch.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                notifyListener("cmd_btnLaunch");
            }
        });*/
    }

    /*public Toolbar getToolbar()
    {
        return (Toolbar) findViewById(R.id.toolbar);
    }*/
}
