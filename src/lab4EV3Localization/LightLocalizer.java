package lab4EV3Localization;

import lejos.hardware.Sound;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.port.Port;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.robotics.SampleProvider;
//Ready for Open Source
/**
 * Light Localizer class uses the light sensor to localize the robot, assuming the robot starts in the lower left quadrant
 * (i.e. in the correct location after running USLocalizer). After localization, the robot should travel to (0, 0, 0).
 * 
 * Tuesday February 7, 2017
 * 9:30am
 * 
 * @author thomaschristinck
 * @author alexmasciotra
 */

public class LightLocalizer {
	public static int ROTATION_SPEED = 80;
	public static int FORWARD_SPEED = 90;

	//This is technically the distance of the sensor from the body sensor, but we kind of use it
	//as a buffer to correct error
	public static double SENSOR_DIST = 2.4;
	private static double BUFFER = 0.05;
	
	//Declare odometer, navigator, and initialize color sensor
	private static final Port colorPort = LocalEV3.get().getPort("S2");	
	private EV3ColorSensor colorSensor = new EV3ColorSensor(colorPort);
	private static SampleProvider sampleProvider;
	private Odometer odo;	
	private Navigation nav;
	
	private EV3LargeRegulatedMotor leftMotor, rightMotor;
	private boolean isNavigating;
	private float sensorAve = 0;
	
	public LightLocalizer(Odometer odo, Navigation nav) {
		this.odo = odo;
		this.nav = nav;
		this.leftMotor = odo.getLeftMotor();
		this.rightMotor = odo.getRightMotor();
		this.isNavigating = false;
	}
	
	public void doLocalization() {
		//Angle array will store angles of each black line sensed
		double [] angle = new double [4]; 
		//Operate sensor in reflection mode
		sampleProvider = colorSensor.getRedMode();
		odo.setTheta(90);
		//Calibrate the sensor - basically read the sensor and see what value we get
		try {
			//Sleep to avoid counting the same line twice
			Thread.sleep(200);
		} catch (InterruptedException e) {}
		
	    calibrateSensorAverage();
	    
	    //Motor speeds
	    leftMotor.setSpeed(FORWARD_SPEED); 
		rightMotor.setSpeed(FORWARD_SPEED);
	    
	    //First we need to get to the position listed in tutorial	
		// The robot will move forward until it detects the next black line
	    forward();	
		while(isNavigating)
		{
			if(blackLineDetected())
				stopMotors();
		}
		
		// The robot then moves forward a set amount and rotates 90 degrees
		moveDistance(SENSOR_DIST, true);
		rotate(-90);
		forward();
		
		// The robot will move forward until it detects the next black line (along x-axis)
	    forward();

		while(isNavigating)
		{
			if(blackLineDetected())
				stopMotors();
		}
		// The robot then moves forward a set amount (slightly greater than before)
		moveDistance(SENSOR_DIST, true);
		rotate();
				
		
	    // Rotate and clock the 4 grid lines
        leftMotor.setSpeed(ROTATION_SPEED);
        rightMotor.setSpeed(ROTATION_SPEED);
        
        int counter = 0;
        while(counter < 4)
		{
        	if (blackLineDetected()){
  				angle[counter] = odo.getTheta();
  				counter++;
  				try {
  					//Sleep to avoid counting the same line twice
  					Thread.sleep(400);
  				} catch (InterruptedException e) {}
  			}
		}
		
		stopMotors();
		
		// Calculates its current position
		double thetaX = angle[2] - angle[0];
		double thetaY = angle[3] - angle[1];
		
		double xPosition = -1*SENSOR_DIST*Math.sin(Math.toRadians(thetaX/2));
		double yPosition= -1*SENSOR_DIST*Math.sin(Math.toRadians(thetaY/2));
		
		//Correct theta, then add to current theta
		//Used 176 degrees instead of 180 to correct theta
		double newTheta = 176 - angle[0]; 
		newTheta += odo.getTheta();
		newTheta = Odometer.fixDegAngle(newTheta);
		
		// Updates odometer to current location
		odo.setPosition(new double [] {xPosition, yPosition, newTheta}, new boolean [] {true, true, true});
		
		// Navigates to (0,0) and turns to 0 degrees
		nav.travelTo(0, 0);
		nav.turnTo(0,true);
	}
	
	// Method moves the robot a set distance either forward or backwards
	private void moveDistance(double distance, boolean isForward){
		int tmp = 0;
		if(isForward)
			tmp = 1;
		else
			tmp = -1;
		leftMotor.rotate(tmp * convertDistance(odo.getRadius(), distance), true);
		rightMotor.rotate(tmp * convertDistance(odo.getRadius(), distance), false);
	}
	
	// Method rotates robot by theta degrees
	private void rotate(double theta){
		leftMotor.rotate(convertAngle(odo.getRadius(), odo.getWidth(), theta), true);
		rightMotor.rotate(-convertAngle(odo.getRadius(), odo.getWidth(), theta), false);
	}
	
	// Method rotates robot counterclockwise
	private void rotate(){
		leftMotor.backward();
		rightMotor.forward();
	}
	
	// Method moves the robot forwards and updates the isNavigating value
	private void forward(){
		leftMotor.forward();	
		rightMotor.forward();
		isNavigating = true;
	}
	
	// Method stops motors and updates isNavigating value
	private void stopMotors(){
		leftMotor.stop(true);
		rightMotor.stop(false);
		isNavigating = false;
	}
	
	private  int convertDistance(double radius, double distance)
	{ 															 
		return (int) ((180.0 * distance) / (Math.PI * radius)); 
	} 
	      
	private  int convertAngle(double radius, double width, double angle) 
	{ 
		return convertDistance(radius, Math.PI * width * angle / 360.0); 
	}
	
	//Calibrates sensor (baseline is average of 4 tile readings)
  	private float calibrateSensorAverage(){
  		float sensorValue = 0;
  		for(int i = 0;i < 4; i++){
  			sensorValue += getColorData();;
  		}
  		sensorValue = (float) (sensorValue * 0.25);
  		this.sensorAve = sensorValue;
  		if (sensorValue < 0.05){
  		//Sleep to avoid counting the same line twice
  			try
  			{
			Thread.sleep(200);
			} catch (InterruptedException e) {}
  			calibrateSensorAverage();
  		}
  		return sensorValue;	
  	}
  	
	// Helper method to detect the black line
  	private boolean blackLineDetected() {
  		float lineCheck = getColorData();
  		//Black line is detected if the color is below the tile's color by a threshold
  		boolean isHit = (lineCheck < sensorAve - BUFFER);
  		if (isHit)
  			Sound.beep();	
  		return isHit; 
  	}

  	// Method returns the value of the color sensor
 	private float getColorData(){
 		//Set up array to collect samples
 		int sampleSize = 1;
 		int offset = 0;
 		float[] sample = new float[sampleSize];
		sampleProvider.fetchSample(sample, offset);
 		return sample[0];
 	}
 	
}