package com.habib.wekaserver;

import java.net.*;

public class Server {

	static DataClassifier classifier;
	static int requestNumber = 1;
	
	public static void main(String[] args) {
		
		classifier = new DataClassifier();
		
		try {
			@SuppressWarnings("resource")
			ServerSocket socket = new ServerSocket(5000);
			System.out.println("Server started on 10.80.64.74 port 5000");
			
			classifier.createModel();
			System.out.println("Classifier model created on server...");
			System.out.println("Waiting for client connections...");

			while(true) {
				Socket newSocket = socket.accept();
				System.out.println("Creating new client thread ...");
				Thread clientThread = new ThreadHandler(newSocket, requestNumber);
				clientThread.start();
				requestNumber++;
			}
			
		} catch (Exception e) {
			System.out.println("IO error " + e);
		}
	}
}