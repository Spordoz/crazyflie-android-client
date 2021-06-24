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

import android.util.Log;

import se.bitcraze.crazyfliecontrol2.MainActivity;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;

/**
 * The TouchController uses the on-screen joysticks to control the roll, pitch, yaw and thrust values.
 * The mapping of the axes can be changed with the "mode" setting in the preferences.
 *
 * For example, mode 3 (default) maps roll to the left X-Axis, pitch to the left Y-Axis,
 * yaw to the right X-Axis and thrust to the right Y-Axis.
 *
 */
public class TouchController extends AbstractController {

    protected int mMovementRange = 1000;  // "resolution"

    protected JoystickView mJoystickViewLeft;
    protected JoystickView mJoystickViewRight;

    private float lastThrust = 0;
    private float lastInput = 0;

    public static boolean stickyThrust = false;
    public static boolean mHover = false;

    public float getThrust() {
        float thrust =  ((mControls.getMode() == 1 || mControls.getMode() == 3) ? mControls.getRightAnalog_Y() : mControls.getLeftAnalog_Y());
        JoystickView joystickView = (mControls.getMode() == 1 || mControls.getMode() == 3) ? mJoystickViewRight : mJoystickViewLeft;
        if (isHover() && mControls.getDeadzone(thrust) == 1) {
            float minThrust = mControls.getMinThrust();
            if (thrust < 0) {
                minThrust = minThrust * -1;
            }
            return minThrust + (thrust * mControls.getThrustFactor());
        } else if(!stickyThrust) {
            if (thrust > mControls.getDeadzone()) {
                Log.d("JoystickView", "Thrust: " + thrust);
                //thrust -= mControls.getDeadzone();
                Log.d("JoystickView", "Thrust after: " + thrust);
                Log.d("JoystickView", "Thrust final: " + (mControls.getMinThrust() + (thrust - 1 * mControls.getDeadzone())/(1 - mControls.getDeadzone()) * mControls.getThrustFactor()));
                return (float) (mControls.getMinThrust() + (thrust - 1 * mControls.getDeadzone())/(1 - mControls.getDeadzone()) * mControls.getThrustFactor());
            } else {
                return 0;
            }
        } else {
            if (Math.abs(thrust) > mControls.getDeadzone()) {
                thrust -= mControls.getDeadzone();
                Log.d("JoystickView", "thrust before: " + Float.toString(thrust));
                lastInput = thrust;
                if (mControls.getMinThrust() + (thrust * mControls.getThrustFactor()) > mControls.getMaxThrust()) {
                    lastThrust = mControls.getMaxThrust();
                    joystickView.capInput = true;
                    return mControls.getMaxThrust();
                } else if (mControls.getMinThrust() + (thrust * mControls.getThrustFactor()) < mControls.getMinThrust()) {
                    lastThrust = 0;
                    joystickView.capInput = true;
                    return 0;
                }
                joystickView.capInput = false;
                Log.d("JoystickView", "thrust: " + Float.toString(thrust));
                lastThrust = mControls.getMinThrust() + (thrust * mControls.getThrustFactor());
                return lastThrust;
            }
            return lastThrust;
        }
    }

    public float getThrustAbsolute() {
        float thrust = getThrust();
        float absThrust = thrust/100 * MAX_THRUST;

        //Hacky Hover Mode
        if(isHover()) {
            Log.d("debug", "sending hover values: " + (32767 + ((absThrust/65535)*32767)));
            return 32767 + ((absThrust/65535)*32767);
        } else {
            if(thrust > 0) {
                return absThrust;
            }
        }
        return 0;
    }

    public boolean isHover() {
        return this.mHover;
    }

    public TouchController(Controls controls, MainActivity activity, JoystickView joystickviewLeft, JoystickView joystickviewRight) {
        super(controls, activity);
        this.mJoystickViewLeft = joystickviewLeft;
        this.mJoystickViewRight = joystickviewRight;
        this.mJoystickViewLeft.setMovementRange(mMovementRange);
        this.mJoystickViewRight.setMovementRange(mMovementRange);
        updateAutoReturnMode();
    }

    private void updateAutoReturnMode() {
        if(stickyThrust){
            this.mJoystickViewLeft.setAutoReturnMode(isLeftAnalogFullTravelThrust() ? JoystickView.AUTO_RETURN_NONE : JoystickView.AUTO_RETURN_CENTER);
        } else {
            this.mJoystickViewLeft.setAutoReturnMode(isLeftAnalogFullTravelThrust() ? JoystickView.AUTO_RETURN_BOTTOM : JoystickView.AUTO_RETURN_CENTER);
        }
        this.mJoystickViewLeft.autoReturn(true);
        this.mJoystickViewRight.setAutoReturnMode(isRightAnalogFullTravelThrust() ? JoystickView.AUTO_RETURN_BOTTOM : JoystickView.AUTO_RETURN_CENTER);
        this.mJoystickViewRight.autoReturn(true);
    }

    @Override
    public void enable() {
        super.enable();
        this.mJoystickViewLeft.setOnJoystickMovedListener(_listenerLeft);
        this.mJoystickViewRight.setOnJoystickMovedListener(_listenerRight);
        updateAutoReturnMode();
    }

    @Override
    public void disable() {
        mControls.setRightAnalogY(0);
        mControls.setRightAnalogX(0);
        mControls.setLeftAnalogY(0);
        mControls.setLeftAnalogX(0);
        this.mJoystickViewLeft.setOnJoystickMovedListener(null);
        this.mJoystickViewRight.setOnJoystickMovedListener(null);
        super.disable();
    }

    public String getControllerName() {
        return "touch controller";
    }

    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

        @Override
        public void OnMoved(float pan, float tilt) {
            if (isRightAnalogFullTravelThrust()) {
                tilt = (tilt + 1.0f) / 2.0f;
            }
            mControls.setRightAnalogY(tilt);

            mControls.setRightAnalogX(pan);

            /*if(!GyroscopeController.YawButtonPressed){
                GyroscopeController.setYawZero();
            }
            GyroscopeController.YawButtonPressed = true;*/
            Log.d("Joystick-Right", Boolean.toString(GyroscopeController.yawButtonPressed));

            updateFlightData();
        }

        @Override
        public void OnReleased() {
            GyroscopeController.yawButtonPressed = false;
            Log.i("Joystick-Right", "Release");
            Log.d("Joystick-Right", Boolean.toString(GyroscopeController.yawButtonPressed));
            //mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
        }

        public void OnReturnedToCenter() {
            // Log.i("Joystick-Right", "Center");
            mControls.setRightAnalogY(0);
            mControls.setRightAnalogX(0);
        }
    };

    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

        @Override
        public void OnMoved(float pan, float tilt) {
            if (isLeftAnalogFullTravelThrust()) {
                if(!stickyThrust){
                    tilt = (tilt + 1.0f) / 2.0f;
                }
            }
            Log.d("JoystickView", Float.toString(tilt));
            mControls.setLeftAnalogY(tilt);

            mControls.setLeftAnalogX(pan);

            updateFlightData();

            if(stickyThrust){
                OnReleased();
            }
        }

        @Override
        public void OnReleased() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }

        public void OnReturnedToCenter() {
            mControls.setLeftAnalogY(0);
            mControls.setLeftAnalogX(0);
        }
    };


    public boolean isThrustRightAnalog() {
        return (mControls.getMode() == 1 || mControls.getMode() == 3);
    }

    public boolean isLeftAnalogFullTravelThrust() {
        return mControls.isTouchThrustFullTravel() && !isThrustRightAnalog();
    }

    public boolean isRightAnalogFullTravelThrust() {
        return mControls.isTouchThrustFullTravel() && isThrustRightAnalog();
    }

}
