package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.steelhead.ftc.AutoRobotFunctions;
import org.steelhead.ftc.HardwareSteelheadMainBot;

/**
 * Demonstrates empty OpMode
 */
@Autonomous(name = "Button Pusher - Blue", group = "Button")
//@Disabled
public class AutoBlueNew extends LinearOpMode {

    private final int TOLERANCE_DEGREES = 1;

    private double MAX_OUTPUT_DRIVE = 1.0;
    private double MIN_OUTPUT_DRIVE = -1.0;
    private double MAX_OUTPUT_ROTATE = 0.25;
    private double MIN_OUTPUT_ROTATE = -0.25;
    private double MAX_OUTPUT_LINE = 0.25;
    private double MIN_OUTPUT_LINE = -0.25;

    private AutoRobotFunctions autoRobotFunctions;

    @Override
    public void runOpMode() throws InterruptedException {

        HardwareSteelheadMainBot robot = new HardwareSteelheadMainBot();

        robot.init(hardwareMap);
        autoRobotFunctions = new AutoRobotFunctions(this, robot);

        autoRobotFunctions.setGyroDrivePID(0.04, 0.00085, 0.0008);
        autoRobotFunctions.setGyroRotatePID(0.0327, 0.0005, 0.0008);

        autoRobotFunctions.setColorPID(0.018, 0.05, 0.00203);


        telemetry.addData("STATUS:", "init complete–check state of gyro");
        telemetry.update();

        //wait for start of the match
        robot.setPoliceLED(true);
        waitForStart();

        robot.robotForward();
        autoRobotFunctions.runWithEncoders(500, 1.0);

        autoRobotFunctions.MRRotate(-40, TOLERANCE_DEGREES,
                MIN_OUTPUT_ROTATE, MAX_OUTPUT_ROTATE);


        autoRobotFunctions.MRDriveStraight(-40, .60,
                MIN_OUTPUT_DRIVE, MAX_OUTPUT_DRIVE, TOLERANCE_DEGREES, 0.0005, 4500, 0.15,
                AutoRobotFunctions.StopConditions.COLOR, 20);

        autoRobotFunctions.PIDLineFollow(6, 55, 0.20, MIN_OUTPUT_LINE, MAX_OUTPUT_LINE, 0,
                AutoRobotFunctions.StopConditions.BUTTON, AutoRobotFunctions.LineSide.LEFT);

        autoRobotFunctions.pushButton(AutoRobotFunctions.Team.BLUE);

        robot.robotBackward();
      //  autoRobotFunctions.runWithEncoders(1000, 0.35);

        //shoot ball
        autoRobotFunctions.MRDriveStraight(90, .6,
                MIN_OUTPUT_DRIVE, MAX_OUTPUT_DRIVE, TOLERANCE_DEGREES, 0.0005, 2500, 0.15,
                AutoRobotFunctions.StopConditions.ENCODER, 2500);

        robot.shooterPower(0.7);

        robot.robotBackward();
        autoRobotFunctions.runWithEncoders(2450, 1.0);

        robot.shooterServo.setPosition(0.6);

        Thread.sleep(500);

        robot.shooterServo.setPosition(1.0);

        robot.shooterPower(0.0);


        robot.robotForward();
        //autoRobotFunctions.pusherActive(true);
        autoRobotFunctions.MRRotate(-20, TOLERANCE_DEGREES,
                MIN_OUTPUT_ROTATE, MAX_OUTPUT_ROTATE);

        autoRobotFunctions.MRDriveStraight(-20, 0.60,
                MIN_OUTPUT_DRIVE, MAX_OUTPUT_DRIVE, TOLERANCE_DEGREES, 0.0005, 3500, 0.15,
                AutoRobotFunctions.StopConditions.COLOR, 20);

        autoRobotFunctions.PIDLineFollow(9, 55, 0.20, MIN_OUTPUT_LINE, MAX_OUTPUT_LINE, 0,
                AutoRobotFunctions.StopConditions.BUTTON, AutoRobotFunctions.LineSide.LEFT);
        autoRobotFunctions.pushButton(AutoRobotFunctions.Team.BLUE);

        autoRobotFunctions.close();

        robot.setPoliceLED(false);
        robot.close();

        telemetry.addData("STATUS:", "Complete");
        telemetry.update();
    }
}

