package ru.d51x.twutil;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import java.util.Date;
import java.text.SimpleDateFormat;

import android.view.View;

import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.CheckBox;

import android.tw.john.TWUtil;
import android.widget.Toast;

public class MainActivity extends Activity {

	private int xwhat = -1;
    private static Handler mHandler;

    private EditText id_edit_what;
    private EditText id_edit_arg1;
    private EditText id_edit_arg2;
    private Button id_button_sendcommand;
    private TextView id_textView_Result;

	private EditText id_edittext_handler;
	private EditText id_edittext_modeid;
	private Button id_button_handler;
	private TextView id_textview_log;

    private TWUtil mTW, mTW2;
	public boolean isHandlerStarted;
    private CheckBox checkbox_show_toast;
    private CheckBox checkbox_get_obj_string;
    private boolean isShowToast = true;
    private boolean isGetObjString = false;

    public MainActivity() {
        this.mTW = null;
	    this.mTW2 = null;
	    this.isHandlerStarted = false;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.id_edit_what = (EditText) findViewById(R.id.id_edit_what);
        this.id_edit_arg1 = (EditText) findViewById(R.id.id_edit_arg1);
        this.id_edit_arg2 = (EditText) findViewById(R.id.id_edit_arg2);

        this.id_button_sendcommand = (Button) findViewById(R.id.id_button_sendcommand);
        this.id_textView_Result = (TextView) findViewById(R.id.id_textView_Result);

		this.id_edittext_handler = (EditText) findViewById(R.id.id_edittext_handler);
		this.id_edittext_modeid = (EditText) findViewById(R.id.id_edittext_modeid);

	    this.id_button_handler = (Button) findViewById (R.id.id_button_handler);
		this.id_textview_log = (TextView) findViewById (R.id.id_textview_log);

        checkbox_show_toast = (CheckBox) findViewById(R.id.checkbox_show_toast);
        checkbox_get_obj_string = (CheckBox) findViewById(R.id.checkbox_get_obj_string);

        this.checkbox_show_toast.setOnClickListener(new CheckBox.OnClickListener() {
            public void onClick(View v) // клик на кнопку
            {
                isShowToast = checkbox_show_toast.isChecked();
            }
        });

        this.checkbox_get_obj_string.setOnClickListener(new CheckBox.OnClickListener() {
            public void onClick(View v) // клик на кнопку
            {
                isGetObjString = checkbox_get_obj_string.isChecked();
            }
        });

        this.id_button_sendcommand.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) // клик на кнопку
            {
	            int what = Integer.parseInt (id_edit_what.getText ().toString ());
	            int arg1 = Integer.parseInt (id_edit_arg1.getText ().toString ());
	            int arg2 = Integer.parseInt (id_edit_arg2.getText ().toString ());
	            //short[] Shorts = new short[] {(short) what};

	            mTW = new TWUtil ();
	            if (mTW.open (new short[]{(short) what}) == 0) {
		            mTW.start ();

		            int res = mTW.write (what, arg1, arg2);
		            id_textView_Result.setText (String.format ("%d", res));
		            // close session
		            mTW.write (what, 255, 0); /// !!! зачем это???
	            }
	            mTW.close ();
	            mTW = null;

            }
        });

	    this.id_button_handler.setOnClickListener(new Button.OnClickListener () {
		    public void onClick (View v) // клик на кнопку
		    {

			    if (isHandlerStarted) {
				    // stop handler
				    // stop and close TWUtil
				    //mTW2.write (xwhat, 255);
				    mTW2.removeHandler ("TWUtilHandler");
				    mTW2.stop ();
				    mTW2.close ();
				    mTW2 = null;
				    isHandlerStarted = false;
				    id_button_handler.setText ("Start Handler");
			    } else {
				    // open and start TWUtil

				    if ( id_edittext_modeid.getText().toString().isEmpty () ) {
					    mTW2 = new TWUtil ();
				    } else {
					    int i = Integer.parseInt(id_edittext_modeid.getText().toString());
					    mTW2 = new TWUtil (i);
				    }

                    short[] shorts;
                    if ( id_edittext_handler.getText().toString().isEmpty ()) {
                        shorts = new short[]{(short) 260, (short) 262, (short) 263, (short) 266, (short) 274, (short) 282, (short) 513, (short) 514, (short) 515, (short) 769, (short) 770, (short) 1281, (short) -25088, (short) -25071, (short) -24816, (short) -24805, (short) -24804};
                    } else {
                        xwhat = Integer.parseInt(id_edittext_handler.getText().toString());
                        shorts = new short[]{(short) xwhat};
                    }

				    if (mTW2.open ( shorts ) == 0) {
					    mTW2.start ();
					    mTW2.addHandler ("TWUtilHandler", new Handler () {
						    public void handleMessage(Message msg) {
							    try {
								    if (msg.what == xwhat ) {
										    Date date = new Date();
										    SimpleDateFormat ft = new SimpleDateFormat ("HH:mm:ss");
									        String sd = ft.format(date);
											String str = String.format("%s -->:  what: %d   arg1: %d  arg2: %d", sd, msg.what, msg.arg1, msg.arg2);
										    id_textview_log.setText ( str + "\n" + id_textview_log.getText ());
									        if ( isShowToast ) Toast.makeText (MainActivity.this, String.format("what: %d, arg1: %d, arg2: %d", msg.what, msg.arg1, msg.arg2), Toast.LENGTH_SHORT).show ();
                                            if ( isGetObjString ) {
                                                try {
                                                    String objtext = (String) msg.obj;

                                                    id_textview_log.setText(objtext + "\n" + id_textview_log.getText());
                                                } catch (Exception e) {

                                                }

                                            }
								    }
							    } catch (Exception e) {
							    }
						    }
						});
						isHandlerStarted = true;
					    id_button_handler.setText ("Stop Handler");
				    }
				    // start handler

			    }
		    }
	    });
    }




    protected void onDestroy() {

        super.onDestroy();
    }

}
