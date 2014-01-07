package com.ffmpeg.rtplay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

/**
 * @author gianni
 * 
 */
public class SocketThread implements Runnable
{
	private Thread thread = null;
	private MulticastSocket socket = null;

	private boolean running = false;

	private int bytesRead = 0;

	public void start(final String url)
	{
		try
		{
			running = true;

			socket = new MulticastSocket(5004);
			socket.setSoTimeout(10000);

			socket.joinGroup(InetAddress.getByName(url));

			thread = new Thread(this);
			thread.start();
		}
		catch (UnknownHostException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		while (true)
		{
			synchronized (this)
			{
				if (!running) break;
			}

			byte[] buffer = new byte[1600];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

			try
			{
				socket.receive(packet);

				synchronized (this)
				{
					bytesRead += packet.getLength();
				}
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void stop()
	{
		synchronized (this)
		{
			running = false;
		}
	}

	public int bytesRead()
	{
		synchronized (this)
		{
			return bytesRead;
		}
	}
}
