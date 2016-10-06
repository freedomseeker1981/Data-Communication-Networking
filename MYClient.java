/* author : Reda Elbahi
 * Project2 TFTP client implementation 
 * Class :Spring 2011 
 * course: Data Network and Communication CS740
 * Description :application implements TFTP protocol , sends a request to read a file from Server
 * glados.cs.rit.edu and fetch the required file . Server sends data upon receiving request and validating
 * the name of the file , server sends one block of data and waits for acknowledgment to be received 
 * data are sent as a block of bytes contains 512 byte of data and last block will be less than
 * connectoin used is UDP
 * some concepts are invoked from university of osnabrueck website
 */
import java.net.*;
import java.nio.file.FileAlreadyExistsException;

import java.io.*;
import java.util.*;


public class MYClient extends Exception {

	// protocol elemnts are constants within the scope 
	public static int MaxPacketLength=516;
	public static int maxdata=512;
	public final static short rreq = 1;  
	public final static short datareq = 3;
	public final static short ackreq = 4;
	public final static short errorreq = 5;
	public  static final int OPStart=0;

	public  static final int FileNameByteStart=2;

	public  static final int BlockNumberStart=2;
	public  static final int DataStart=4;

	public  static final int ErrorNumberStart=2;
	public  static final int ErrorMessageStart=4;

	public static int NumberOfPacket=0;
	
	public  static int NumberOfBytes=512;

	public  int messageLenght;
	public  byte [] message;


	public  InetAddress host; // IP ADDRESS OF MY HOST SERVER
	public  int ServerPort;// PORT TATH SERVER USES

	public static void main(String argv[]) throws  IOException {
		String host=null;

		try {

			System.out.println(" Enter Your Server Host Name :");

			Scanner scan = new Scanner(System.in);

			host= scan.nextLine();
			System.out.println(" Enter Your File Name :");

			scan = new Scanner(System.in);

			String fileName= scan.next(); 

			String TransferMode= null;
			boolean ind = true;

			while(ind){			 	
				System.out.println(" \n Which Transfer Mode ?"+"\n 1- octet " 
						+ " 2- netascii");

				scan = new Scanner(System.in);

				String  choice= scan.next();


				if(choice.charAt(0) == '1' ){
					TransferMode = "octet"; 
					ind = false;}
				else  if ( choice.charAt(0) == '2' ){
					TransferMode  = "netascii";
					ind = false;}
				System.out.println(" mode is " + TransferMode);
			}

			DatagramSocket DataSock = new DatagramSocket();
			InetAddress serverIP = InetAddress.getByName(host);// compile the ip address of required host
			System.out.println(" server ip is " + serverIP);


			System.out.println(" datagram socket is " + DataSock.getLocalPort());
			FileOutputStream outFile = new FileOutputStream(fileName);// the file created to be received



			MYClient Transporter = new MYClient();

			Transporter.SendRequestMessage(serverIP,DataSock,fileName ,TransferMode);// start read request


			for (; NumberOfBytes==512; NumberOfPacket++) {


				short flag = (short) Transporter.RceiveFromServer(DataSock);// start receiving data from server

				if((flag )==0)	 // error message
				{
					throw  new TFTPError(Transporter); // process error message 
				}
				else if (flag == 1){

					int CurrentBlockNumber=Transporter.GetBlockNumber();
					NumberOfBytes=Transporter.writeToFile(outFile);// convert bytes from octet bytes to file format required

					Transporter.sendAcknowledgement(
							Transporter.getAddress(),Transporter.getPort()
							,DataSock,CurrentBlockNumber);// send a block acknowledgment to server
				}
				else{
					System.err.println(" Sever Error");
					System.exit(1);
				}


			}
			System.out.println("num of bytes" + NumberOfBytes);

			outFile.close();
			DataSock.close();
		}
		catch (UnknownHostException e) { System.out.println("Unknown host "+host);// host is not recognized
		}
		catch (FileAlreadyExistsException k){ System.out.println(" file not created");// file is already existed
		}

		catch (IOException e) { System.out.println(" error, new establishment aborted");// error in connection
		}
		catch (TFTPError e) {
			System.out.println("TFTP System Error " ); // tftp protocol problem

		}


	}


	public MYClient() {

		message=new byte[MaxPacketLength];
		messageLenght=MaxPacketLength;
	}

	// Method to send and receive packets

	protected int RceiveFromServer(DatagramSocket sock)
			throws IOException {

		short flag ;
		int newMessageLength = MaxPacketLength;
		byte[] NewMessage = new byte[MaxPacketLength] ;

		DatagramPacket MyPacket = new DatagramPacket(
				NewMessage,newMessageLength);

		sock.receive(MyPacket);
		this.AnalyzePacket(MyPacket,NewMessage);

		switch (this.getMessageByte(0)) {

		case datareq:

			flag = 1;// received packet is a block of data
			break;

		case errorreq:
			flag = 0;// received packet is error message


			break;
		default : flag = 9;// system error
		}
		return flag;
	}

	// get the port number , ip address , message and its length from the recieved packet
	protected void AnalyzePacket(DatagramPacket myPacket,byte[] NewMessage){

		host=myPacket.getAddress();
		ServerPort=myPacket.getPort();
		messageLenght = myPacket.getLength();
		message = NewMessage;
	}

	// fill the packet header format by approperiate request type and / or blocknumber
	protected void FillPacketHeader(int PacketStart, short value) {
		this.message[PacketStart++] = (byte)(value >>> 8);  
		this.message[PacketStart] = (byte)(value % 256);
	}

	// fill packet with the message wanted to be sent 
	protected void FillPacketBody(int at, String MessageValue, byte EndOfSegment) {
		MessageValue.getBytes(0, MessageValue.length(), message, at);
		message[at + MessageValue.length()] = EndOfSegment;
	}

// prepare and send the read request message to server
	public void SendRequestMessage(InetAddress ip, DatagramSocket sock ,String RequestedFileName
			,String RequestType) throws IOException {

		messageLenght=2+RequestedFileName.length()+1+RequestType.length()+1;
		message = new byte[messageLenght];

		FillPacketHeader(OPStart,rreq);
		FillPacketBody(FileNameByteStart,RequestedFileName,(byte)0);
		FillPacketBody(FileNameByteStart+RequestedFileName.length()+1,RequestType,(byte)0);
		sock.send(new DatagramPacket(message,messageLenght,ip,69));
	}

	// send acknowledgment about certain block
	public void sendAcknowledgement
	(InetAddress ip, int port,
			DatagramSocket SendSocket, int currentBlockNumber) throws IOException {
		messageLenght=4;
		this.message = new byte[messageLenght];

		FillPacketHeader(OPStart,ackreq);
		FillPacketHeader(BlockNumberStart,(short)currentBlockNumber);
		SendSocket.send(new DatagramPacket(message,messageLenght,ip,port));
	}
// convert data to file format
	public int writeToFile(FileOutputStream NewFile) throws IOException {
		NewFile.write(message,DataStart,messageLenght-4);
		return(messageLenght-4);
	}
	// bring host name ip address
	public InetAddress getAddress() {
		return host;
	}
// bring the port number of server
	public int getPort() {
		return ServerPort;
	}
// get the length of current message 
	public int getLength() {
		return messageLenght;
	}

// get the content of certain byte from the message
	protected int getMessageByte(int loc) {
		return (message[loc] & 0xff) << 8 | message[loc+1] & 0xff;
	}
// get the text message from certain bytes
	protected String GetMessage (int start, byte endOfMessage) {
		StringBuffer messageBuffer = new StringBuffer();

		while (message[start] != endOfMessage) 
			messageBuffer.append((char)message[start++]);

		return messageBuffer.toString();
	}

// get error number from packet header 
	public int GetErrorNumber() {
		return this.getMessageByte(ErrorNumberStart);// use for error reference
	}

// get blocknumber from the message
	public int GetBlockNumber() {
		return this.getMessageByte(BlockNumberStart);
	}

	// get the error message from the error block
	public String GetErrorMessage() {
		return this.GetMessage(ErrorMessageStart,(byte)0);
	}

}

// class to handle tftp error messages
class TFTPError extends MYClient {
	public TFTPError() { super(); }
	public TFTPError(MYClient Error) { 
		System.out.println(" Error Number " + Error.GetErrorNumber());
		System.out.println(" Error Message : " + Error.GetErrorMessage());


	}
}

