package com.habib.wekaserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ThreadHandler extends Thread {

	private Socket socket;
	private int clientNumber;
	String temp_file = "/weka-3-9-0/myData/temp_data.arff";

	ThreadHandler(Socket socket, int clientNumber) {
		this.socket = socket;
		this.clientNumber = clientNumber;
	}

	public void run() {

		try {

			PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			System.out.println("Connected to client number: " + clientNumber);

			printWriter.println("CONNECTED");

			boolean hasData = true;
			String clientData;

			while (hasData) {
				
				clientData = bufferedReader.readLine();

				if (clientData != null && !clientData.startsWith("QUIT")) {

					PrintWriter out = new PrintWriter("/home/habib/weka-3-9-0/myData/temp_data.arff");
					
					out.println("@relation temp_data");
					out.println("");
					
					out.println("@attribute Ax numeric");
					out.println("@attribute Ay numeric");
					out.println("@attribute Az numeric");
					out.println("@attribute Gx numeric");
					out.println("@attribute Gy numeric");
					out.println("@attribute Gz numeric");
					out.println("@attribute Mx numeric");
					out.println("@attribute My numeric");
					out.println("@attribute Mz numeric");
					out.println("@attribute Activity {Downstairs,Running,Sitting,Standing,Upstairs,Walking}");
					out.println("");
					
					out.println("@data");
					out.println(clientData);

					out.close();

					Server.classifier.loadDataset(temp_file);

					String predictedClass = Server.classifier.getClassValue();

					printWriter.println(predictedClass + "\n");
					
					//System.out.println("Class predicted: " + predictedClass);
					
					Server.classifier.deleteFile(temp_file);
					
				} else if(clientData.startsWith("QUIT")){
					//if (clientData.trim().equals("QUIT")){
						hasData = false;
					//}
				}
			}
			socket.close();
			System.out.println("Disconnected from client number: " + clientNumber);
			Server.requestNumber -= 1;
		} catch (Exception e) {
			System.out.println("IO error " + e);
		}
	}
}