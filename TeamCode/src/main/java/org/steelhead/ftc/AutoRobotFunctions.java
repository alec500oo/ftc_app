package org.steelhead.ftc;

import android.util.Log;

import com.kauailabs.navx.ftc.AHRS;
import com.kauailabs.navx.ftc.navXPIDController;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.ColorSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.TouchSensor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.hardware.modernrobotics.ModernRoboticsI2cGyro;

/**
 * Created by Alec Matthews on 11/6/2016.
 * This class is made to simplify the robots autonomous programs.
 */

public class AutoRobotFunctions {

    //PID Values for the navX sensor
    private double navKPdrive;
    private double navKIdrive;
    private double navKDdrive;

    private double navKPturn;
    private double navKIturn;
    private double navKDturn;

    //PID Values for the Modern Robotics Sensor
    private double colorKP;
    private double colorKI;
    private double colorKD;

    private double gyroRotateKP;
    private double gyroRotateKI;
    private double gyroRotateKD;

    private double gyroDriveKP;
    private double gyroDriveKI;
    private double gyroDriveKD;

    //Movement components of the robot including a robot hardware class
    private HardwareSteelheadMainBot robot;
    private DcMotor leftMotor;
    private DcMotor rightMotor;

    //NavX Sensor
    private final byte NAVX_DEVICE_UPDATE_RATE_HZ = 50;
    private final int DEVICE_TIMEOUT_MS = 500;
    private AHRS navXDevice;

    //Rest of the sensors
    private LinearOpMode currentOpMode;
    private ColorSensor color;
    private Adafruit_ColorSensor beaconColor;
    private TouchSensor touchSensor;
    private ModernRoboticsI2cGyro gyro;

    public enum StopConditions {COLOR, ENCODER, BUTTON}

    public enum Team {RED, BLUE}

    public enum LineSide {LEFT, RIGHT}

    private String TAG = "AUTOROBOT:";

    @Deprecated
    public AutoRobotFunctions(byte navXDevicePortNumber, HardwareMap hardwareMap,
                              LinearOpMode currentOpMode, HardwareSteelheadMainBot robot) {
        boolean calibrationComplete = false;
        this.robot = robot;
        this.currentOpMode = currentOpMode;
        this.leftMotor = robot.leftMotor;
        this.rightMotor = robot.rightMotor;
        this.touchSensor = robot.touchSensor;
        this.color = robot.color;
        this.gyro = robot.gyro;
        this.beaconColor = robot.beaconColor;

        //Setup the navX sensor and wait for calibration to complete
        navXDevice = AHRS.getInstance(hardwareMap.deviceInterfaceModule.get("dim"),
                navXDevicePortNumber, AHRS.DeviceDataType.kProcessedData,
                NAVX_DEVICE_UPDATE_RATE_HZ);

      /*  while (!calibrationComplete && currentOpMode.opModeIsActive()) {
            calibrationComplete = !navXDevice.isCalibrating();
            currentOpMode.telemetry.addData("CAL: ", "NavX device calibrating");
            currentOpMode.telemetry.update();
        }

        if (!navXDevice.isConnected()) {
            currentOpMode.telemetry.addData("ERROR: ", "NavX not connected");
            currentOpMode.telemetry.update();
        }
        navXDevice.zeroYaw();*/
    }

    public AutoRobotFunctions(LinearOpMode currentOpMode, HardwareSteelheadMainBot robot,
                              String logTag) {
        this.robot = robot;
        this.currentOpMode = currentOpMode;
        this.leftMotor = robot.leftMotor;
        this.rightMotor = robot.rightMotor;
        this.touchSensor = robot.touchSensor;
        this.color = robot.color;
        this.gyro = robot.gyro;
        this.beaconColor = robot.beaconColor;
        navXDevice = null;

        TAG += logTag;

        gyro.calibrate();
        currentOpMode.telemetry.addData("Gyro", "Calibrating. Do Not Move!!");
        currentOpMode.telemetry.update();
        while (!currentOpMode.isStopRequested() && gyro.isCalibrating()) {
            currentOpMode.idle();
        }

        currentOpMode.telemetry.addData("Gyro", "Calibration Complete");
        currentOpMode.telemetry.update();
    }

    //MR Gyro rotate PID
    public void MRRotate(int degree, int tolerance,
                         double minMotorOutput, double maxMotorOutput) {
        ElapsedTime rotateTime = new ElapsedTime();
        boolean rotationComplete = false;
        double angle = 0;

        GyroPIDController pidController = new GyroPIDController(this.gyro, degree, tolerance,
                TAG + ":Rotate");
        pidController.setPID(gyroRotateKP, gyroRotateKI, gyroRotateKD);
        pidController.enable();

        try {
            Thread.sleep(10);
            rotateTime.reset();
            while (!rotationComplete && currentOpMode.opModeIsActive()) {
                angle = gyro.getIntegratedZValue();
                if (pidController.isOnTarget()) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    rotationComplete = true;
                } else {
                    double output = pidController.getOutput();

                    leftMotor.setPower(limit(output, minMotorOutput, maxMotorOutput));
                    rightMotor.setPower(limit(-output, minMotorOutput, maxMotorOutput));

                    currentOpMode.telemetry.addData("Output", output);
                    if (rotateTime.milliseconds() > 4000) {
                        currentOpMode.telemetry.addData(">", "reevaluate your life choices!");
                        Log.i(TAG + ":Rotate", "Rotate Timed out!!");
                        rotationComplete = true;
                    }
                }
                currentOpMode.telemetry.addData("Time to error", rotateTime.milliseconds());
                currentOpMode.telemetry.addData("Yaw", angle);
                currentOpMode.telemetry.update();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        } finally {
            pidController.disable();
        }
    }

    //PID controller for MR Gyro
    public boolean MRDriveStraight(int degree, double driveSpeed, double minOutputVal,
                                   double maxOutputVal, int tolerance, double motorSpeedMul,
                                   int encoderDistance, double minEndPower,
                                   StopConditions stopCondition, int stopVal,
                                   int maxEncoderDistance) {
        double workingForwardSpeed = driveSpeed;
        double output = 0;
        boolean pidEnable = true;
        robot.stopAndClearEncoders();
        robot.enableEncoders(true);

        GyroPIDController pidController = new GyroPIDController(this.gyro, degree, tolerance,
                TAG + ":Drive Straight");
        pidController.setPID(gyroDriveKP, gyroDriveKI, gyroDriveKD);
        pidController.enable();

        try {
            //Sleep to allow the pid controller to calculate a first value
            Thread.sleep(10);
            while (currentOpMode.opModeIsActive()) {
                currentOpMode.telemetry.addData("Gyro Yaw", gyro.getIntegratedZValue());

                //Check for different stop conditions and handle them appropriately
                if (stopCondition == StopConditions.COLOR && color.alpha() > stopVal) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                } else if (stopCondition == StopConditions.ENCODER &&
                        rightMotor.getCurrentPosition() >= stopVal) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                } else if (stopCondition == StopConditions.BUTTON && touchSensor.isPressed()) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                }

                //Get the PID controller output and apply it to the motors
                if (pidEnable) {
                    output = pidController.getOutput();
                    if (pidController.isOnTarget()) {
                        leftMotor.setPower(workingForwardSpeed);
                        rightMotor.setPower(workingForwardSpeed);
                        currentOpMode.telemetry.addData("Output", workingForwardSpeed);
                    } else {
                        if (robot.isRobotBackward()) {
                            double leftSpeed = limit((workingForwardSpeed - output), minOutputVal, maxOutputVal);
                            double rightSpeed = limit((workingForwardSpeed + output), minOutputVal, maxOutputVal);

                            currentOpMode.telemetry.addData("Output", output);
                            currentOpMode.telemetry.addData("Left Speed", leftSpeed);
                            currentOpMode.telemetry.addData("Right Speed", rightSpeed);
                            leftMotor.setPower(leftSpeed);
                            rightMotor.setPower(rightSpeed);
                        } else {
                            double leftSpeed = limit((workingForwardSpeed + output), minOutputVal, maxOutputVal);
                            double rightSpeed = limit((workingForwardSpeed - output), minOutputVal, maxOutputVal);

                            currentOpMode.telemetry.addData("Output", output);
                            currentOpMode.telemetry.addData("Left Speed", leftSpeed);
                            currentOpMode.telemetry.addData("Right Speed", rightSpeed);
                            leftMotor.setPower(leftSpeed);
                            rightMotor.setPower(rightSpeed);
                        }
                    }
                } else {
                    leftMotor.setPower(workingForwardSpeed);
                    rightMotor.setPower(workingForwardSpeed);
                }

                //check to see if the robot overshoots the line
                if (maxEncoderDistance != -1 && rightMotor.getCurrentPosition() >= maxEncoderDistance) {
                    pidController.disable();
                    return false;
                }

                /*
                 * Slow the robot as it gets close to the line so it does not overshoot,
                 * This is basically a P controller.
                 * If the multiplier is equal to -1 turn off the speed reduction
                 */
                if (motorSpeedMul != -1) {
                    if (rightMotor.getCurrentPosition() >= (encoderDistance - 500)) {
                        int error = (encoderDistance - rightMotor.getCurrentPosition()) - 500;
                        workingForwardSpeed = driveSpeed + error * motorSpeedMul;
                        if (workingForwardSpeed < minEndPower) {
                            workingForwardSpeed = minEndPower;
                            pidEnable = false;
                        }
                    }
                }
                currentOpMode.telemetry.addData("Right Encoder", rightMotor.getCurrentPosition());
                currentOpMode.telemetry.addData("Left Encoder", leftMotor.getCurrentPosition());
                currentOpMode.telemetry.update();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pidController.disable();
            return true;
        }
    }

    //NavX PID controller for rotation to a degree
    @Deprecated
    public void navxRotateToDegree(double degree, double tolerance,
                                   double minMotorOutput, double maxMotorOutput) {
        boolean rotationComplete = false;
        robot.robotSetZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        navXPIDController yawPIDController = new navXPIDController(navXDevice,
                navXPIDController.navXTimestampedDataSource.YAW);

        yawPIDController.setSetpoint(degree);
        yawPIDController.setContinuous(true);
        yawPIDController.setOutputRange(minMotorOutput, maxMotorOutput);
        yawPIDController.setTolerance(navXPIDController.ToleranceType.ABSOLUTE, tolerance);
        yawPIDController.setPID(navKPturn, navKIturn, navKDturn);

        navXPIDController.PIDResult yawPIDResult = new navXPIDController.PIDResult();
        yawPIDController.enable(true);

        try {
            while (!rotationComplete && currentOpMode.opModeIsActive()
                    && !Thread.currentThread().isInterrupted()) {
                if (yawPIDController.waitForNewUpdate(yawPIDResult, DEVICE_TIMEOUT_MS)) {
                    if (yawPIDResult.isOnTarget()) {
                        leftMotor.setPower(0);
                        rightMotor.setPower(0);
                        rotationComplete = true;
                    } else {
                        double output = yawPIDResult.getOutput();
                        leftMotor.setPower(output);
                        rightMotor.setPower(-output);
                    }
                }
                currentOpMode.telemetry.addData("YAW: ", navXDevice.getYaw());
                currentOpMode.telemetry.update();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        yawPIDController.enable(false);
        yawPIDController.close();
        robot.robotSetZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
    }


    //Drive straight with a PID controller
    @Deprecated
    public void navXDriveStraight(double degree, double tolerance,
                                  double minOutputRage, double maxOutputRange,
                                  double driveSpeed, int encoderDistance,
                                  double motorSpeedMul, double minEndPower,
                                  StopConditions stopCondition, int stopVal) {
        double workingForwardSpeed = driveSpeed;
        //double rampMul = driveSpeed / 500;
        boolean rampComplete = true;
        ElapsedTime rampTime = new ElapsedTime();
        //Enable and clear the encoders
        robot.stopAndClearEncoders();
        robot.enableEncoders(true);

        //Setup the yaw PID controller
        navXPIDController yawPIDController = new navXPIDController(navXDevice,
                navXPIDController.navXTimestampedDataSource.YAW);

        yawPIDController.setSetpoint(degree);
        yawPIDController.setContinuous(true);
        yawPIDController.setOutputRange(minOutputRage, maxOutputRange);
        yawPIDController.setTolerance(navXPIDController.ToleranceType.ABSOLUTE, tolerance);
        yawPIDController.setPID(navKPdrive, navKIdrive, navKDdrive);
        yawPIDController.enable(true);
        navXPIDController.PIDResult yawPIDResult = new navXPIDController.PIDResult();

        rampTime.reset();
        try {
            while (currentOpMode.opModeIsActive() && !Thread.currentThread().isInterrupted()) {

                currentOpMode.telemetry.addData("Right Encoder", rightMotor.getCurrentPosition());
                currentOpMode.telemetry.addData("Left Encoder", leftMotor.getCurrentPosition());
                //ramp the motor up to prevent damage and jerk

                /*if (!rampComplete && rampTime.milliseconds() <= 500) {
                    int error = (int) rampTime.milliseconds();
                    workingForwardSpeed = error * rampMul;
                    if (workingForwardSpeed > driveSpeed) {
                        workingForwardSpeed = driveSpeed;
                        rampComplete = true;
                    }
                }*/
                if (stopCondition == StopConditions.COLOR && color.alpha() > stopVal) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                } else if (stopCondition == StopConditions.ENCODER) {
                    if (rightMotor.getCurrentPosition() >= stopVal) {
                        leftMotor.setPower(0);
                        rightMotor.setPower(0);
                        break;
                    }
                } else if (stopCondition == StopConditions.BUTTON && touchSensor.isPressed()) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                }
                if (yawPIDController.waitForNewUpdate(yawPIDResult, DEVICE_TIMEOUT_MS)) {
                    if (yawPIDResult.isOnTarget()) {
                        leftMotor.setPower(workingForwardSpeed);
                        rightMotor.setPower(workingForwardSpeed);
                        currentOpMode.telemetry.addData("Yaw", "On Target");
                    } else {
                        double output = yawPIDResult.getOutput();
                        leftMotor.setPower(limit((workingForwardSpeed + output), minOutputRage, maxOutputRange));
                        rightMotor.setPower(limit((workingForwardSpeed - output), minOutputRage, maxOutputRange));
                        currentOpMode.telemetry.addData("Output", output);
                        currentOpMode.telemetry.addData("Yaw", navXDevice.getYaw());
                        currentOpMode.telemetry.addData("Target", degree);
                    }
                } else {
                    currentOpMode.telemetry.addData("navx: ", "DEVICE TIME OUT!");
                }
            /*
            Slow the robot as it gets close to the line so it does not overshoot,
            This is basically a P controller.
            If the multiplier is equal to -1 turn off the speed reduction
            */

                if (motorSpeedMul != -1) {
                    if (rampComplete && rightMotor.getCurrentPosition() >= (encoderDistance - 500)) {
                        int error = (encoderDistance - rightMotor.getCurrentPosition()) - 500;
                        workingForwardSpeed = driveSpeed + error * motorSpeedMul;
                        if (workingForwardSpeed < minEndPower) {
                            workingForwardSpeed = minEndPower;
                        }

                    }
                }
                currentOpMode.telemetry.update();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            yawPIDController.enable(false);
            robot.stopAndClearEncoders();
            robot.enableEncoders(false);
            yawPIDController.close();
        }
    }

    //PID controller for following a line
    public void PIDLineFollow(int threshHoldLow, int threshHoldHigh,
                              double driveSpeed, double minOutputVal,
                              double maxOutputVal, double tolerance,
                              StopConditions stopConditions, LineSide lineSide) {
        ColorPIDController pidController = new ColorPIDController(this.color,
                threshHoldLow, threshHoldHigh, TAG + ":Line Follow");
        pidController.setPID(colorKP, colorKI, colorKD);
        pidController.setTolerance(tolerance);
        pidController.enable();

        try {
            //Sleep to allow the pid controller to calculate a first value
            Thread.sleep(100);
            while (currentOpMode.opModeIsActive()) {
                if (stopConditions == StopConditions.BUTTON && touchSensor.isPressed()) {
                    leftMotor.setPower(0);
                    rightMotor.setPower(0);
                    break;
                }
                double output = pidController.getOutput();
                currentOpMode.telemetry.addData("Output", output);
                if (lineSide == LineSide.LEFT) {
                    leftMotor.setPower(limit((driveSpeed - output), minOutputVal, maxOutputVal));
                    rightMotor.setPower(limit((driveSpeed + output), minOutputVal, maxOutputVal));
                } else {
                    leftMotor.setPower(limit((driveSpeed + output), minOutputVal, maxOutputVal));
                    rightMotor.setPower(limit((driveSpeed - output), minOutputVal, maxOutputVal));
                }
                currentOpMode.telemetry.update();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pidController.disable();
        }
    }

    //Drive to an encoder limit
    public void runWithEncoders(int targetPosition, double motorPower) {
        boolean rampComplete = false;
        ElapsedTime rampTime = new ElapsedTime();
        ElapsedTime logRate = new ElapsedTime();
        double rampUpMul = motorPower / 500;
        double rampDownMul = motorPower / 30;
        double workingForwardSpeed = 0;

        robot.stopAndClearEncoders();
        robot.enableEncoders(true);

        robot.leftMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        leftMotor.setTargetPosition(targetPosition);
        rightMotor.setTargetPosition(targetPosition);

        rampTime.reset();
        leftMotor.setPower(motorPower);
        rightMotor.setPower(motorPower);
        while (currentOpMode.opModeIsActive() && leftMotor.isBusy() && rightMotor.isBusy()) {
            //Ramp the motor to start with
            if (!rampComplete && rampTime.milliseconds() <= 30) {
                int error = (int) rampTime.milliseconds();
                workingForwardSpeed = error * rampUpMul;
                if (workingForwardSpeed >= motorPower) {
                    workingForwardSpeed = motorPower;
                    rampComplete = true;
                }
                leftMotor.setPower(workingForwardSpeed);
                rightMotor.setPower(workingForwardSpeed);
            }
            //Ramp down as the robot approaches the target.
            if (rampComplete && rightMotor.getCurrentPosition() >= (targetPosition - 30)) {
                int error = (targetPosition - rightMotor.getCurrentPosition()) - 30;
                workingForwardSpeed = motorPower - error * rampDownMul;
                if (workingForwardSpeed < 0.1) {
                    workingForwardSpeed = 0.1;
                }
                leftMotor.setPower(workingForwardSpeed);
                rightMotor.setPower(workingForwardSpeed);
            }
            currentOpMode.telemetry.addData("ENC left: ", leftMotor.getCurrentPosition());
            currentOpMode.telemetry.addData("ENC right: ", rightMotor.getCurrentPosition());
            currentOpMode.telemetry.update();

            if (logRate.milliseconds() >= 500) {
                logRate.reset();
                Log.i(TAG + ":Encoder", String.format("SPEED: %f | ENC LEFT: %d | ENC RIGHT: %d",
                        workingForwardSpeed, leftMotor.getCurrentPosition(),
                        rightMotor.getCurrentPosition()));
            }
        }
        leftMotor.setPower(0);
        rightMotor.setPower(0);

        robot.stopAndClearEncoders();
        robot.enableEncoders(false);
    }

    //Push the proper color button for the team the robot is on
    public void pushButton(Team team) {
        try {
            if (team == Team.RED) {
                currentOpMode.telemetry.addData("Blue Color", beaconColor.blueColor());
                currentOpMode.telemetry.addData("Red Color", beaconColor.redColor());

                Log.i(TAG, String.format("Blue Color: %d", beaconColor.blueColor()));
                Log.i(TAG, String.format("Red Color: %d", beaconColor.redColor()));

                if (beaconColor.blueColor() > beaconColor.redColor()) {
                    currentOpMode.telemetry.addData("Team: RED", "sees BLUE, pushing opposite");
                    Log.i(TAG, "Team: RED, " + "sees BLUE, pushing opposite");

                    robot.pusherRight.setPosition(0.1);
                } else {
                    currentOpMode.telemetry.addData("RED", "sees RED, pushing");
                    Log.i(TAG, "Team: RED, " + "sees RED, pushing");

                    robot.pusherLeft.setPosition(0.9);
                }
            } else {
                currentOpMode.telemetry.addData("Blue Color", beaconColor.blueColor());
                currentOpMode.telemetry.addData("Red Color", beaconColor.redColor());

                Log.i(TAG, String.format("Blue Color: %d", beaconColor.blueColor()));
                Log.i(TAG, String.format("Red Color: %d", beaconColor.redColor()));

                if (beaconColor.blueColor() > beaconColor.redColor()) {
                    currentOpMode.telemetry.addData("BLUE", "sees BLUE, pushing");
                    Log.i(TAG, "Team: BLUE, " + "sees BLUE, pushing");

                    robot.pusherLeft.setPosition(0.9);
                } else {
                    currentOpMode.telemetry.addData("BLUE", "sees RED, pushing opposite");
                    Log.i(TAG, "Team: BLUE, " + "sees RED, pushing opposite");

                    robot.pusherRight.setPosition(0.1);
                }
            }
            currentOpMode.telemetry.update();
            Thread.sleep(500);
            robot.pusherRight.setPosition(0.9);
            robot.pusherLeft.setPosition(0.1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //gyro z axis reset function
    public void resetGyroAngle() {
        gyro.resetZAxisIntegrator();
    }

    @Deprecated
    public void pusherActive(boolean state) {
        if (state) {
            robot.pusherRight.setPosition(0.2);
            robot.pusherLeft.setPosition(0.8);
        } else {
            robot.pusherRight.setPosition(0.8);
            robot.pusherLeft.setPosition(0.2);
        }
    }

    public void close() {
        if (navXDevice != null) {
            navXDevice.close();
        }
    }

    //Set the PID values for the NavX sensor
    @Deprecated
    public void setNavXPIDDriveStraight(double Kp, double Ki, double Kd) {
        this.navKPdrive = Kp;
        this.navKIdrive = Ki;
        this.navKDdrive = Kd;
    }

    @Deprecated
    public void setNavXPIDTurn(double Kp, double Ki, double Kd) {
        this.navKPturn = Kp;
        this.navKIturn = Ki;
        this.navKDturn = Kd;
    }

    //Set the PID values for all of the PID controllers
    public void setColorPID(double Kp, double Ki, double Kd) {
        this.colorKP = Kp;
        this.colorKI = Ki;
        this.colorKD = Kd;
    }

    public void setGyroRotatePID(double Kp, double Ki, double Kd) {
        this.gyroRotateKP = Kp;
        this.gyroRotateKI = Ki;
        this.gyroRotateKD = Kd;
    }

    public void setGyroDrivePID(double Kp, double Ki, double Kd) {
        this.gyroDriveKP = Kp;
        this.gyroDriveKI = Ki;
        this.gyroDriveKD = Kd;
    }

    //Limit function
    private double limit(double a, double minOutputVal, double maxOutputVal) {
        return Math.min(Math.max(a, minOutputVal), maxOutputVal);
    }
}
