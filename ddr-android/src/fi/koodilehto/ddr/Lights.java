package fi.koodilehto.ddr;

import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

public class Lights {

	private final byte intensities[];
	private FileOutputStream out;
	private final byte head[];
	private final byte tail[] = {0x7e,0x02};
	
	/**
	 * Constructs a new light controller with no outStream and with given
	 * lightCount.
	 * @param lightCount Number of lights in receiving device.
	 */
	public Lights(int lightCount) {
		this.intensities = new byte[lightCount];
		
		if (lightCount==0x7e) {
			head = new byte[5];
			head[0] = 0x7e;
			head[1] = 0x01;
			head[2] = 0x00;
			head[3] = 0x7e;
			head[4] = 0x00;
		} else {
			head = new byte[4];
			head[0] = 0x7e;
			head[1] = 0x01;
			head[2] = 0x00;
			head[3] = (byte)lightCount;
		}
	}
	
	/**
	 * Changes output stream. This may be a case if the process gets stopped and the connection dies.
	 * @param outStream New stream
	 */
	public void changeStream(FileOutputStream outStream) {
		try {
			if (this.out != null) {
				this.out.close();
			}
		} catch (IOException e) {
			// Do nothing if already closed.
		}
		this.out = outStream;
	}
	
	public void destroyStream() {
		try {
			out.close();
			out = null;
		} catch (IOException e) {
			Log.d(DdrLightsActivity.TAG, "light device close failed");
		}
	}
	
	/**
	 * Sets given light intensity.
	 * @param lightID Light id to change
	 * @param intensity Intensity, in range of 0..1. Allows range overflow but
	 * leads to saturation. 
	 */
	public void set(int lightID, float intensity) {
		int rawInt = Math.round(intensity*256);
		
		if (rawInt < 0x00)
			set(lightID,(byte)0x00);
		else if (rawInt > 0xff)
			set(lightID,(byte)0xff);
		else
			set(lightID,(byte)rawInt);
	}
	
	/**
	 * Sets given light on or off.
	 * @param lightID Light id to change
	 * @param state New state
	 */
	public void set(int lightID, boolean state) {
		set(lightID, state ? (byte)0xff : (byte)0x00);
	}
	
	/**
	 * Sets given light intensity.
	 * @param lightID lightID Light id to change
	 * @param intensity Intensity, in range of 0..255. For additional
	 * confusion: Please note that byte is signed and 255 is in fact -127. :-D
	 */
	public void set(int lightID,byte intensity) {
		if (lightID < 0 || lightID > intensities.length)
			throw new RuntimeException("Light ID out of bounds: "+lightID);
		intensities[lightID] = intensity; 
	}
	
	/**
	 * Updates the state of hardware. Sends WRITE and REFRESH to the hardware.
	 * Updates all data even if no changes have happened.
	 */
	public void refresh() throws IOException {
		if (out == null) return;
		
		out.write(head);
		for (int i=0; i<intensities.length; i++) {
			out.write(intensities[i]);
			// If was escape, send literal escape, too.
			if (intensities[i] == 0x7e)	out.write(0x00);
		}
		out.write(tail);
	}
}
