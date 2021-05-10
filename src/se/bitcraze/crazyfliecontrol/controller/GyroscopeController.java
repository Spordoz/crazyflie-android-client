/**
 *    ||          ____  _ __
 * +------+      / __ )(_) /_______________ _____  ___
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol.controller;

import se.bitcraze.crazyfliecontrol2.MainActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

/**
 * The GyroscopeController extends the TouchController and uses the gyroscope sensors
 * of the device to control the roll and pitch values.
 * The yaw and thrust values are still controlled by the touch controls according
 * to the chosen "mode" setting.
 *
 */
public class GyroscopeController extends TouchController {


    private SensorManager mSensorManager;
    private Sensor mSensor = null;
    private SensorEventListener mSeListener = null;


    private float mSensorRoll = 0;
    private float mSensorPitch = 0;
    private static float mSensorYaw = 0;
    private static float mSensorYawZero = 0;
    //private float mSensorPitchZero = 0;
    private float lastThrust = 0;
    private float lastInput = 0;
    //private boolean yawZeroSet = false;
    public static boolean yawButtonPressed = false;
    public static boolean onlyYawOnPressed = false;
    public static boolean useGyroYaw = false;

    public GyroscopeController(Controls controls, MainActivity activity, JoystickView joystickviewLeft, JoystickView joystickviewRight) {
        super(controls, activity, joystickviewLeft, joystickviewRight);
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);

        if (mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSeListener = new RotationVectorListener();
        } else if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSeListener = new AccelerometerListener();
        }
        if (mSensor != null) {
            Log.i("GyroscopeController", "Gyro sensor type: " + mSensor.getName());
        }
    }

    @Override
    public void enable() {
        super.enable();
        if (mSensor != null && mSeListener != null) {
            mSensorManager.registerListener(mSeListener, mSensor, 100000);
//            mSensorManager.registerListener(mSeListener, mSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void disable() {
        mSensorRoll = 0;
        mSensorPitch = 0;
        mSensorYaw = 0;
        if (mSeListener != null) {
            mSensorManager.unregisterListener(mSeListener);
        }
        super.disable();
    }

    public String getControllerName() {
        return "gyroscope controller";
    }

    class AccelerometerListener implements SensorEventListener {
        //It divide back the 90 degree.
//        private final float AMPLIFICATION = 1.5f;
        private final float AMPLIFICATION = mControls.getGyroAmplification();

        @Override
        public void onAccuracyChanged(Sensor sensor, int arg1) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            float d = (float) Math.max(Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]),0.001);
            mSensorPitch = event.values[0] / d * -1f * AMPLIFICATION;
            mSensorRoll = event.values[1] / d * AMPLIFICATION;
            mSensorYaw = event.values[2] / d * AMPLIFICATION;
            updateFlightData();
        }
    }

    class RotationVectorListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                if (event.values.length > 4) {
                    float[] truncatedRotationVector = new float[4];
                    System.arraycopy(event.values, 0, truncatedRotationVector, 0, 4);
                    update(truncatedRotationVector);
                } else {
                    update(event.values);
                }
            }
        }

    }

    private static final int FROM_RADS_TO_DEGS = -57;

    private void update(float[] vectors) {
        int AMP_MAX = 50;
        float AMPLIFICATION = AMP_MAX / mControls.getGyroAmplification();
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, vectors);
        int worldAxisX = SensorManager.AXIS_X;
        int worldAxisY = SensorManager.AXIS_Y;
        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisX, worldAxisY, adjustedRotationMatrix);
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);
        float pitch = (orientation[2] * FROM_RADS_TO_DEGS * -1) / AMPLIFICATION;
        float roll = (orientation[1] * FROM_RADS_TO_DEGS) / AMPLIFICATION;
        float yaw = (orientation[0] * FROM_RADS_TO_DEGS * -1) / AMPLIFICATION;
        mSensorRoll = roll;
        mSensorPitch = pitch;
        mSensorYaw = yaw;
        //Log.d("YAW", Float.toString(yaw));
        updateFlightData();

    }

    public float getThrust() {
        float thrust =  ((mControls.getMode() == 1 || mControls.getMode() == 3) ? mControls.getRightAnalog_Y() : mControls.getLeftAnalog_Y());
        JoystickView joystickView = (mControls.getMode() == 1 || mControls.getMode() == 3) ? mJoystickViewRight : mJoystickViewLeft;
        if (Math.abs(thrust) > mControls.getDeadzone()) {
            thrust -= mControls.getDeadzone();
            Log.d("JoystickView", "thrust before: " + Float.toString(thrust));
            lastInput = thrust;
            if(mControls.getMinThrust() + (thrust * mControls.getThrustFactor()) > mControls.getMaxThrust()){
                lastThrust = mControls.getMaxThrust();
                joystickView.capInput = true;
                return mControls.getMaxThrust();
            } else if(mControls.getMinThrust() + (thrust * mControls.getThrustFactor()) < mControls.getMinThrust()){
                lastThrust = 0;
                joystickView.capInput = true;
                return 0;
            }
            joystickView.capInput = false;
            Log.d("JoystickView", "thrust: " + Float.toString(thrust));
            lastThrust = mControls.getMinThrust() + (thrust * mControls.getThrustFactor());
            return lastThrust;


//            lastThrust = thrust;
//            return thrust;
        }
        return lastThrust;
    }

    // overwrite getRoll() and getPitch() to only use values from gyro sensors
    public float getRoll() {
        float roll = mSensorRoll;

        //Filter the overshoot
        roll = (float) Math.min(1.0, Math.max(-1, roll+mControls.getRollTrim()));

        return (roll + mControls.getRollTrim()) * mControls.getRollPitchFactor() * mControls.getDeadzone(roll);
    }

    public float getPitch() {
        float pitch = mSensorPitch;

        //Filter the overshoot
        pitch = (float) Math.min(1.0, Math.max(-1, pitch+mControls.getPitchTrim()));

        return (pitch + mControls.getPitchTrim()) * mControls.getRollPitchFactor() * mControls.getDeadzone(pitch);
    }

    public static void setYawZero(){
        //if (!yawZeroSet) {
        mSensorYawZero = mSensorYaw;
        //yawZeroSet = true;
        Log.d("YAW", "zero" + Float.toString(mSensorYawZero));
        //}
    }

//    public static void setPitchZero(){
//        //if (!yawZeroSet) {
//        mSensorPitchZero = mSensorPitch;
//        //yawZeroSet = true;
//        Log.d("YAW", "zero" + Float.toString(mSensorYawZero));
//        //}
//    }

    // map Yaw to the second touch pad
    public float getYaw() {
        Log.d("Joystick-Right", "bool press only: " + Boolean.toString(onlyYawOnPressed));
        if(useGyroYaw){
            if(onlyYawOnPressed) {
                if (yawButtonPressed) {
                    float yaw = mSensorYaw - mSensorYawZero;
                    yaw = (float) Math.min(1.0, Math.max(-1, yaw + mControls.getYawTrim()));
                    return (yaw + mControls.getYawTrim()) * mControls.getYawFactor() * mControls.getDeadzone(yaw);
                } else {
                    return 0;
                }
            } else{
                float yaw = mSensorYaw - mSensorYawZero;
                yaw = (float) Math.min(1.0, Math.max(-1, yaw + mControls.getYawTrim()));
                return (yaw + mControls.getYawTrim()) * mControls.getYawFactor() * mControls.getDeadzone(yaw);
            }
        } else {
            float yaw = 0;
            yaw = (mControls.getMode() == 1 || mControls.getMode() == 2) ? mControls.getRightAnalog_X() : mControls.getLeftAnalog_X();
            return yaw * mControls.getYawFactor() * mControls.getDeadzone(yaw);
        }
        //Log.d("YAW", "yaw return:" + (yaw + mControls.getYawTrim()) * mControls.getYawFactor() * mControls.getDeadzone(yaw));
    }
}
