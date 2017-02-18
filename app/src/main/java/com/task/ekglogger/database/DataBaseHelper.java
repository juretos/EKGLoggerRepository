package com.task.ekglogger.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by Jure on 17.2.2017.
 */

public class DataBaseHelper extends SQLiteOpenHelper {
    private String TAG ="DatabaseHelper";
    public SQLiteDatabase db;
    private Context ctx;
    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "ekgdatabase.db";

    // Table Names
    private static final String TABLE_RR_INTERVALS= "rr_intervals";

    // Common column names
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TIMESTAMP= "timestamp";
    private static final String COLUMN_RR_VALUE = "rr_value";


    //create rr table
    private static final String CREATE_TABLE_RR_INTERVALS = "CREATE TABLE "
            + TABLE_RR_INTERVALS + "(" + COLUMN_ID + " INTEGER PRIMARY KEY autoincrement," +  COLUMN_TIMESTAMP + " NUMERIC,"
            + COLUMN_RR_VALUE + " INTEGER" + ")";


    public DataBaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        db = this.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        // creating required tables
        d.execSQL(CREATE_TABLE_RR_INTERVALS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldVersion, int newVersion) {
        // on upgrade drop older tables
        d.execSQL("DROP TABLE IF EXISTS " + TABLE_RR_INTERVALS);
        // create new tables
        onCreate(d);
    }


    /*
     * Insert into RR database
     */
    public long insertRRdata(long date, int rr) {
        long rowInserted=-1;
        try{
            if(!db.isOpen())
                db=this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, date);
            values.put(COLUMN_RR_VALUE, rr);
            // insert row
            rowInserted = db.insert(TABLE_RR_INTERVALS, null, values);
        }catch(Exception e){
            e.printStackTrace();
        }
        finally {
            if(db!=null && db.isOpen()) db.close();
        }
        return rowInserted;
    }

    public double getAverageRRData(int limit){
        double result = -1;
        int number = 0;
        int sum = 0;
        try {
            if(!db.isOpen())
                db = this.getReadableDatabase();
            String selectQuery= "SELECT * FROM "+ TABLE_RR_INTERVALS + " ORDER BY "+COLUMN_TIMESTAMP+" DESC LIMIT "+limit;
            Cursor cursor = db.rawQuery(selectQuery, null);
            if(cursor.getCount()>0) {
                if (cursor.moveToFirst()) {
                    do{
                        sum+=cursor.getInt(cursor.getColumnIndex(COLUMN_RR_VALUE));
                        number++;
                    } while(cursor.moveToNext());
                }
            }
        }catch(Exception e){
            result=-2;
            Log.d(TAG, "Error in DataBaseAdapter, method getOldestWellbeingFactorGroupDate().");
        }
        finally {
            if(db!=null && db.isOpen()) db.close();
        }

        if(number==0|sum==0){
            return result;
        }
        return sum/number;

    }
}
