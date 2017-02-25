package com.example.android.sunshine;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static com.example.android.sunshine.Utils.getSmallArtResourceIdForWeatherCondition;

public class WeatherWatchface extends CanvasWatchFaceService {

    // Typeface of the hour
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Variables for the syncronization of the data with the app
     */
    private static final String WATCHFACE_DATA_PATH = "/watchface_data_path";
    private static final String KEY_MAX_TEMPERATURE = "MAX_TEMPERATURE";
    private static final String KEY_MIN_TEMPERATURE = "KEY_MIN_TEMPERATURE";
    private static final String KEY_WEATHER_ID = "KEY_MIN_TEMPERATURE";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";
        static final String DATE_FORMAT_STRING = "EEE, MMM d yyyy";
        
        final GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchface.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat(DATE_FORMAT_STRING, Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
                invalidate();
            }
        };

        boolean mRegisteredReceiver = false;
        Paint mBackgroundPaint;
        float mLineHeight;
        // date
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Calendar mCalendar;
        Date mDate;
        java.text.DateFormat mDateFormat;
        // colon
        Paint mColonPaint;
        float mColonWidth;
        // tempetrature
        Paint mMaxTempPaint;
        Paint mMinTempPaint;
        int maxTemperature;
        int minTemperature;
        // line separtor
        Paint mLineSeparatorPaint;
        float lineSeparatorHalfWidth;
        float lineSeparatorPadding;
        // weather icon
        Bitmap weatherIcon;
        int weatherIconWidth;
        int weatherIconHeight;
        int weatherIconPadding;
        int weatherId;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WeatherWatchface.this.getResources();
            mLineHeight = resources.getDimension(R.dimen.line_height);
            lineSeparatorHalfWidth = resources.getDimension(R.dimen.line_separator_half_width);
            lineSeparatorPadding = resources.getDimension(R.dimen.line_separator_padding);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.interactive_background_color));

            mDatePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.date_color));
            mHourPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.hour_color));
            mMinutePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.minute_color));
            mColonPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.colon_color));
            mMaxTempPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.max_temp_color));
            mMinTempPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.min_temp_color));
            mLineSeparatorPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.line_separator_color));

            // get weather info from shared prefences
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(WeatherWatchface.this);
            maxTemperature = sp.getInt(KEY_MAX_TEMPERATURE, 0);
            minTemperature = sp.getInt(KEY_MIN_TEMPERATURE, 0);
            weatherId = sp.getInt(KEY_WEATHER_ID, 500);

            // weather icon
            weatherIconWidth = (int) resources.getDimension(R.dimen.weather_icon_width);
            weatherIconHeight = (int) resources.getDimension(R.dimen.weather_icon_height);
            weatherIconPadding = (int) resources.getDimension(R.dimen.weather_icon_padding);
            setWeatherIcon(getSmallArtResourceIdForWeatherCondition(weatherId));

            // init calendar
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mDateFormat = new SimpleDateFormat(DATE_FORMAT_STRING, Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        // set the weather icon depending on the weather conditions
        private void setWeatherIcon(int iconId) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.outWidth = weatherIconWidth;
            options.outHeight = weatherIconHeight;
            options.inScaled = false;
            weatherIcon = BitmapFactory.decodeResource(getResources(), iconId, options);
        }

        // create the paint with a color and a typeface
        private Paint createTextPaint(int color) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDateFormat = new SimpleDateFormat(DATE_FORMAT_STRING, Locale.getDefault());
                mDateFormat.setCalendar(mCalendar);
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
        }

        // Receiver for when the timezone changes
        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            WeatherWatchface.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            WeatherWatchface.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            // set the text's sizes
            Resources resources = WeatherWatchface.this.getResources();
            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mHourPaint.setTextSize(resources.getDimension(R.dimen.hour_text_size));
            mMinutePaint.setTextSize(resources.getDimension(R.dimen.hour_text_size));
            mColonPaint.setTextSize(resources.getDimension(R.dimen.colon_text_size));
            mMaxTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mMinTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            // update the variables related to special conditions
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            invalidate();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            // all black
            mBackgroundPaint.setColor(inAmbientMode ? Color.BLACK : ContextCompat.getColor(getApplicationContext(), R.color.interactive_background_color));

            // not antialias on low bit ambient
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
                mMaxTempPaint.setAntiAlias(antiAlias);
                mMinTempPaint.setAntiAlias(antiAlias);
            }
            invalidate();
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // get the time
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            String hourString = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = String.format("%02d", mCalendar.get(Calendar.MINUTE));
            String dateString = mDateFormat.format(mDate).toUpperCase();

            // Draw the background
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Calculate the margins to center the text
            float currentY = (bounds.height() - // the height
                    (mLineHeight * 3 + lineSeparatorPadding)) // minus the heights of the texts
                    / 2 + mLineHeight; // divided by two
            float currentX = (bounds.width() - // the width
                    (mHourPaint.measureText(hourString) + mColonWidth + mMinutePaint.measureText(minuteString))) // minus the width of the time
                    / 2; // divided by two

            // Draw the hour, colons and minutes
            canvas.drawText(hourString, currentX, currentY, mHourPaint);
            currentX += mHourPaint.measureText(hourString);
            canvas.drawText(COLON_STRING, currentX, currentY, mColonPaint);
            currentX += mColonWidth;
            canvas.drawText(minuteString, currentX, currentY, mMinutePaint);

            // Only render the date and weather if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {

                // update x and y
                currentX = (bounds.width() - mDatePaint.measureText(dateString)) / 2;
                currentY += mLineHeight;

                // Date
                canvas.drawText(
                        dateString,
                        currentX, currentY , mDatePaint);

                // if we are not in ambient mode we draw the weather
                if (!isInAmbientMode()) {
                    currentY += lineSeparatorPadding;
                    float halfWidth = bounds.width() / 2;
                    canvas.drawLine(halfWidth - lineSeparatorHalfWidth, currentY, halfWidth + lineSeparatorHalfWidth, currentY, mLineSeparatorPaint);

                    // Max and min temperature
                    String maxTempString = String.format("%d° ", maxTemperature);
                    String minTempString = String.format("%d°", minTemperature);
                    float maxTempDrawWidth = mMaxTempPaint.measureText(maxTempString);
                    float minTempDrawWidth = mMinTempPaint.measureText(minTempString);

                    // weather icon
                    currentX = (bounds.width() - (maxTempDrawWidth + minTempDrawWidth + weatherIconWidth + weatherIconPadding)) / 2;
                    Rect iconRect = new Rect((int) currentX, (int)  (currentY + weatherIconPadding), (int) (currentX + weatherIconWidth), (int) (currentY + weatherIconPadding + weatherIconHeight));
                    canvas.drawBitmap(weatherIcon, null, iconRect, null);

                    // draw min temp
                    currentY += mLineHeight;
                    currentX += weatherIconWidth + weatherIconPadding;
                    canvas.drawText(
                            maxTempString,
                            currentX, currentY, mMaxTempPaint);
                    // draw min temp
                    currentX += maxTempDrawWidth;
                    canvas.drawText(
                            minTempString,
                            currentX, currentY, mMinTempPaint);
                }
            }
        }

        /*
        Google API
         */
        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(WATCHFACE_DATA_PATH)) {
                    continue;
                }

                // we are interested only of the data event that has the Uri equaling WATCHFACE_DATA_PATH
                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap weatherInfo = dataMapItem.getDataMap();
                // get the three variables
                maxTemperature = weatherInfo.getInt(KEY_MAX_TEMPERATURE);
                minTemperature = weatherInfo.getInt(KEY_MIN_TEMPERATURE);
                weatherId = weatherInfo.getInt(KEY_WEATHER_ID);
                setWeatherIcon(getSmallArtResourceIdForWeatherCondition(weatherId));
                // save in shared preferences
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(WeatherWatchface.this);
                SharedPreferences.Editor editor = sp.edit();
                editor.putInt(KEY_MAX_TEMPERATURE, maxTemperature);
                editor.putInt(KEY_MIN_TEMPERATURE, minTemperature);
                editor.putInt(KEY_WEATHER_ID, weatherId);
                editor.apply();
                // invalidate the drawing
                invalidate();
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            // listen to changes in data
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int cause) {}

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult result) {}
    }
}

