import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.io.OutputStream;
import java.util.List;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.util.Timer;
import java.util.TimerTask;


import java.util.Collections;
import java.util.Comparator;

//socket information:
//https://www.oracle.com/java/technologies/jpl2-socket-communication.html
public class peerProcess {
   //The peer partaking in the process
    static Peer currPeer;
    private ServerSocket serverSocket;
    static Socket requestSocket;
    static ObjectOutputStream out;        
 	static ObjectInputStream in;          
	String message;                
	String MESSAGE;        
    static String fileName;  
   // message typemessage;
    handShake handshake;
    static ServerSocket acceptingConnections;
    static byte[] fileData;
    //static RandomAccessFile writeFile;
    //Information to load into the config file
    //Data structures being used (WIP)
    static fileLoader configInfo = new fileLoader();
    //HashMap<Integer, Peer> connectedPeers;
    //HashMap<Integer, Thread> connectedThreads;
    Vector<Peer> activePeers = new Vector<Peer>();
    //track neighbors bitfield by bitfield msg and update by have msg.
    static HashMap<Integer, BitSet> peerBitfields = new HashMap<>();
    //update status interest or not
    static Timer timer = new Timer();
    static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    static boolean interestShown;




    public static void main(String[] args) throws IOException{
        if(args.length == 0)
        {
            System.out.println("Invalid arguments");
        }
        else
        {
            fileLoader loader = new fileLoader();
            //Loads information from common.cfg and peer.info
            configInfo.loadCommon();
            //This specifically contains the map of the peers
            configInfo.loadpeerInfo();
             Integer inputID = Integer.parseInt(args[0]);
             peerVerifier(inputID);
             System.out.println("Running peer: " + currPeer.getpeerID());
             if(currPeer.hasFile() == 1){
                currPeer.bitField.set(0, fileLoader.gettotalPieces());
                String folderName = "peer_" + currPeer.getpeerID();
                loadFile(folderName);
                System.out.println("server FILE DATA LENGTH:" + fileData.length);    
                fileName = "peer_" + currPeer.getpeerID() + "/" + configInfo.getfileName();

             }
             else {
                fileData = new byte[configInfo.getfileSize()];
                System.out.println("CLIENT FILE DATA LENGTH:" + fileData.length);
                fileName = "peer_" + currPeer.getpeerID() + "/" + configInfo.getfileName();
                System.out.println(fileName);
             }
             beginListening();
             beginSearching();

        }

    }
    

    //Uses some of the client code 
    
    public static void hostOpen() throws UnknownHostException, IOException
    {
        for (Integer peerID : configInfo.getpeerMap().keySet()) 
        {
            if(currPeer.getpeerID() == peerID)
            {
                break;
            }
            else
            {
          
            Peer workingPeer = configInfo.getpeerMap().get(peerID);
            String address = workingPeer.gethostName();
            System.out.println("i break here?" + peerID);
            requestSocket = new Socket("localhost", peerID);
            System.out.println("socket at" + peerID);
            new Handler(requestSocket, peerID).start();
            System.out.println("Connected to " + peerID);
           

            }
    }
    }
    public static void beginSearching()
    {

       Runnable hostOpen = () -> {
           try {
            hostOpen();
           } catch (IOException e) {
               e.printStackTrace();
           }
       };

       Thread listeningThread = new Thread(hostOpen);
       listeningThread.start();

   }
    public static void peerVerifier(Integer peerID) 
    {
        boolean peerFound = false;
        for(Integer inputID: configInfo.getpeerMap().keySet())
        {
            if(inputID.equals(peerID))
            {
              System.out.println("Running peer: " + peerID);
              peerFound = true;
              currPeer = configInfo.getpeerMap().get(peerID);
            }
        }
        if(!peerFound)
        {
            System.out.println("Peer not found :[");
        }
    }

    public static void beginListening()
     {

        Runnable serverStart = () -> {
            try {
                serverStart();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        Thread listeningThread = new Thread(serverStart);
        listeningThread.start();

    }


    
    //Uses some of the server code in the example
    public static void serverStart() throws IOException
    {
        acceptingConnections = new ServerSocket(currPeer.getpeerID());
        System.out.println("ServerSocket opened at ID: " + currPeer.getpeerID());
        System.out.println("The server is running."); 
        //Going to have to get the start of a map to determine how many connections have to be made as the rubric says
        //this will happen later though.
       // int connectionstobeMade = configInfo.getPeerMap().start
        for (Integer peerID : configInfo.getpeerMap().keySet()) {
            if(currPeer.getpeerID() == peerID){  
            }
            else{

                new Handler(acceptingConnections.accept(), peerID).start();
				System.out.println("Client "  + (peerID)+ " is connected!");
    
            }
        }
    
    }






     private static class Handler extends Thread {
        private String message;    //message received from the client
		private String MESSAGE;    //uppercase message send to the client
		private Socket connection;
        private  ObjectInputStream in;	//stream read from the socket
        private   ObjectOutputStream out;    //stream write to the socket
		private int no;		//The index number of the client
        private byte[] payload;
        byte[] fileInfo;
        volatile BitSet receivedbitField;
        static boolean chokeSent = false;
        int test = 0;
        boolean unchoked = false;
        private int PeerID;
        int pieceOffset;
        int fileOffset;
        boolean hasFile;
        int total = 0;
        static boolean timerRunning = false;
        static HashMap<Integer, Boolean> peerInterestStatus = new HashMap<>();  
        static HashMap<Integer, Integer> downloadRates = new HashMap<>();
        static  List<Integer> interestedPeers = new ArrayList<>();
        volatile HashMap<Integer, Integer> highestdownloadRates = new HashMap<>();
        //ArrayList<Integer> avaliablePieces = new ArrayList<>();



        int downloadRate;


        	public Handler(Socket connection, int no) {
            		this.connection = connection;
	    		this.no = no;
        	}

            
        public void run()
         {
 		try
        {
            
            //System.out.println(connection);
            RandomAccessFile fileWrite = new RandomAccessFile(fileName, "rw"); 
			out = new ObjectOutputStream(connection.getOutputStream());
			in = new ObjectInputStream(connection.getInputStream());
            sendHandshake();
            

            if(currPeer.hasFile() == 1){
                hasFile = true;
              //  byte[] bitFieldconvert = currPeer.getbitField().toByteArray();
                System.out.println("Sending bitfield connection to " + connection);
                Message message = new Message(MessageType.BITFIELD, currPeer.getbitField().toByteArray());
                byte[] toSend = message.encode();
                System.out.println(toSend.length);
                out.writeObject(toSend);
                out.flush(); // Flush the stream to ensure all data is sent
                System.out.println("File data sent successfully.");
                //out.writeObject(currPeer.getbitField());
                //out.flush(); // Flush the stream to ensure all data is sent
            }
            System.out.println("or do i make it out?");
            System.out.println(currPeer.hasFile());
           // in = new ObjectInputStream(connection.getInputStream());
            
			try
            {
                System.out.println("do i get stuck here?");
                byte[] incomingHandshake = (byte[]) in.readObject();
                handShake decoded = handShake.decode(incomingHandshake);
                downloadRates.put(decoded.getPeerId(), downloadRate);
              

                PeerID=decoded.getPeerId();
                System.out.println("I AM A HOST TO " + PeerID);
                System.out.println("This is the header SERVERSIDE: " + decoded.getHeader());
                System.out.println("SERVERS FILEDATA LENGTH:" +  fileData.length);
                System.out.println("This is the ID SERVERSIDE: " + decoded.getPeerId());
                System.out.println("This is the zero bits length SERVERSIDE: " + decoded.getzerobitsLength());

              //  private static final TimerTask task = new TimerTask()
             
            
            // Start the TimerTask within your initializer
         //   Timer timer = new Timer();
          //  timer.scheduleAtFixedRate(new TimerTask() {
            //    @Override
             //   public void run() {


                //FOR CALCULATING PEERS
                 if(timerRunning == false){
                     timerRunning = true;
                    Timer timer = new Timer();
                    timer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            System.out.println(test);
                            test++;
                            interestedPeers = selectPreferredNeighbors();
                         
                                if(interestedPeers.size() >= configInfo.getnumNeighbors()){
                                    if(currPeer.hasFile() == 1){
    
                                    }
                                    System.out.println("do i make it here");
                                    interestShown = true;
                                    chokeSent = true;
    
    
                                }
                            
                         
                           // List<Map.Entry<Integer, Integer>> interestedpeersdownloadRates = new ArrayList<>();
    
    
                            // for (Map.Entry<Integer, Boolean> entry : peerInterestStatus.entrySet()) {
                            //     int peerID = entry.getKey();
                            //     boolean isInterested = entry.getValue();
                            //         if (isInterested && downloadRates.containsKey(peerID)) {
                            //         highestdownloadRates.put(peerID, downloadRates.get(peerID));
                            //         System.out.println("Peer " + peerID + " is interested.");
                            //     }
                            // }
                         
                            for (Map.Entry<Integer, Integer> entry : downloadRates.entrySet()) {
                                int peerId = entry.getKey();
                                int rate = entry.getValue();
                                System.out.println("Peer ID: " + peerId + ", Download Rate: " + rate);
                                
                            }
    
    
    
    
                        }
                    }, 0, configInfo.getunchokeInterval() * 1000);
                }

                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println(test);
                        System.out.println("this is the PEERID" + PeerID);
                        test++;
                        for (int i = 0; i < interestedPeers.size(); i++) {
                            Integer peer = interestedPeers.get(i);
                            System.out.println("Interested peer: " + peer);
                        }
                          if(interestedPeers.contains(PeerID)){
                        System.out.println("am i ever here");
                         interestShown = false;

                         sendUnchoke();
                     }
                      




                    }
                }, 0, configInfo.getunchokeInterval() * 1000);
               
               
                
               

				while(true)
				{
                 

                    // for (Map.Entry<Integer, Integer> entry : downloadRates.entrySet()) {
                    //     int peerId = entry.getKey();
                    //     int rate = entry.getValue();
                    //     System.out.println("Peer ID: " + peerId + ", Download Rate: " + rate);
                    // }
                    //sleep(2000);
                    // if(interestedPeers.contains(PeerID)){
                    //     System.out.println("am i ever here");
                    //     interestShown = false;

                    //     sendUnchoke();
                    // }
                byte[] incomingMessage = (byte[]) in.readObject();
                Message decodedMessage = Message.decode(incomingMessage); // Create an instance and then call decode
                payload=decodedMessage.getPayload();
                System.out.println(decodedMessage.getType());

                    switch(decodedMessage.getType()){
                        case CHOKE:
                        System.out.println("get choke msg");
                        unchoked = false;
                        break;

                        case UNCHOKE:
                        System.out.println("get no choke msg");
                        if(unchoked){
                            continue;
                        }
                        else { 
                            unchoked = true;
                        if(!hasFile){
                            System.out.println("DO I MAKE IT IN HERE AT ALL");
                         ArrayList<Integer> unchokeavailablePieces = new ArrayList<>();
                         for(int i = 0; i < receivedbitField.length(); i++){
                             if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                                
                                unchokeavailablePieces.add(i);
                               
    
                             }
        

                         }
                         Random random = new Random();
                         int randomunchokeIndex = random.nextInt(unchokeavailablePieces.size());
                          System.out.println("requested new at " + randomunchokeIndex);
                          System.out.println("buffer groverflow");
                          System.out.println("sending request message:");
                          requestMessage(randomunchokeIndex);
                          downloadRate++;
                          break;
                        }
                        break;
                    }
                        case INTERESTED:
                        updateInterestStatus(PeerID, true);
                        interestShown = true;
                        System.out.println(PeerID + "is interested :D");
                        break;
                    
                        case NOT_INTERESTED:
                        updateInterestStatus(PeerID, false);
                        System.out.println(PeerID + "is NOT interested :[");
                        break;
                        
                        case BITFIELD:
                        System.out.println("get bitfiled msg");

                        receivedbitField = BitSet.valueOf(decodedMessage.getPayload());
                        BitSet bitfield = BitSet.valueOf(payload);
                        peerBitfields.put(PeerID, receivedbitField);

                        if(isInterestedInPeer(PeerID)){
                            sendInterestedMessage();
                        }
                        else{
                            sendNotInterestedMessage();
                        }
                        break;





                        //CODE FOR TESTING FILE SENDING
                    //      receivedbitField = BitSet.valueOf(decodedMessage.getPayload());
                    //       System.out.println("this is the length: " + receivedbitField.length());
                    //      for(int i = 0; i < receivedbitField.length(); i++){
                    //         if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                    //         System.out.println("found at value" + i);
                    //         System.out.println("buffer groverflow");
                    //         requestMessage(i);
                    //         break;

                    //     }
    
                    // }
                    // break;
                          //  System.out.println("get bitfiled msg");
                            


                        case HAVE:
                        payload = decodedMessage.getPayload();                   
                        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
                        int pieceIndex = payloadBuffer.getInt();
                        BitSet neighborBitField = peerBitfields.getOrDefault(PeerID, new BitSet());
                        neighborBitField.set(pieceIndex); 
                        peerBitfields.put(PeerID, neighborBitField);

                        if (!currPeer.getbitField().get(pieceIndex)) {
                            
                            sendInterestedMessage();
                            
                        } else {
                            
                            sendNotInterestedMessage();
                        }
                        break;

                        case REQUEST:
                        ByteBuffer buffer = ByteBuffer.wrap(decodedMessage.getPayload());
                        int index = buffer.getInt();
                        System.out.println("REQUSTED INDEX" + index);
                        int offset = index * configInfo.getpieceSize();
                        byte[] requestedPiece = new byte[configInfo.getpieceSize()];

                        if(index == (configInfo.getfileSize()/configInfo.getpieceSize())){
                            int fileLength = configInfo.getfileSize() - offset;
                            //System.out.println("IS THIS 4002 OR WHATEVER IT WAS SUPPOSED TO BE" + (configInfo.getfileSize() - offset));
                            requestedPiece = new byte[fileLength];
                            System.out.println("file offset" + offset);
                            System.out.println("TOTAL SIZE:" + fileData.length);
                            System.arraycopy(fileData, offset, requestedPiece, 0, fileLength);                            //https://www.geeksforgeeks.org/system-arraycopy-in-java/

                            pieceMessage(index, requestedPiece);
                            downloadRates.put(PeerID, downloadRate++);
                            break;
                        }
                        
                     
                        System.arraycopy(fileData, offset, requestedPiece, 0, configInfo.getpieceSize()); //https://www.geeksforgeeks.org/system-arraycopy-in-java/
                        pieceMessage(index, requestedPiece);
                        downloadRates.put(PeerID, downloadRate++);
                        break;


                        
                        case PIECE:
                        int forloopSize = 0;
                        Vector<Integer> availablePieces = new Vector<>();
                        payload = decodedMessage.getPayload();
                        ByteBuffer pieceBuffer = ByteBuffer.wrap(payload);
                         pieceOffset = pieceBuffer.getInt(); // Extract the offset from the first 4 bytes
                         System.out.println("got" + pieceOffset);
                         fileOffset = pieceOffset * configInfo.getpieceSize();
                         System.out.println("piece offset:" + pieceOffset);
                         System.out.println("total pieces:" + configInfo.gettotalPieces());
                         if((pieceOffset+1) == configInfo.gettotalPieces()){
                            int bytes = configInfo.getfileSize() - fileOffset;
                            System.out.println("bytes i guess" + bytes);
                            System.arraycopy(payload, 4, fileData, fileOffset, bytes); 
                            fileWrite.seek(fileOffset);
                            fileWrite.write(payload, 4, configInfo.getpieceSize());
                            System.out.println("ONLY NUMBER PRESENT IN CURRpEER SHOULD BE THIS" + pieceOffset);
                            currPeer.getbitField().set(pieceOffset);
                         }
                         else{
                            System.arraycopy(payload, 4, fileData, fileOffset, configInfo.getpieceSize()); 
                            fileWrite.seek(fileOffset);
                            fileWrite.write(payload, 4, configInfo.getpieceSize());
                            System.out.println("ONLY NUMBER PRESENT IN CURRpEER SHOULD BE THIS" + pieceOffset);
                            currPeer.getbitField().set(pieceOffset);
                         }
                  
                        // currPeer.getbitField().set(2);
                        // currPeer.getbitField().set(3);

                        // System.out.println("CURR PEER BITFIELD SIZE:" + currPeer.getbitField().size());
                        // System.out.println("RECEIVED BITFIELD SIZE:" + receivedbitField.size());
                        // System.out.println("piece size:" + configInfo.gettotalPieces());
                        // System.out.println("PEER BITFIELD IDK WHAT THIS IS GONA LOOK LIKE " + currPeer.getbitField());
                        // System.out.println("PEER BITFIELD IDK WHAT THIS IS GONA LOOK LIKE " + (receivedbitField));
                        //  if(!currPeer.getbitField().get(2) && receivedbitField.get(2)){
                        //      System.out.println("idk wtf is going on here");
                        //  }


                        availablePieces.clear();
                        availablePieces = new Vector<>();
                       // System.out.println("SIZE OF LIST BEFORE" + availablePieces.size());
                        for(int i = 0; i < configInfo.gettotalPieces(); i++){
                            if( !currPeer.getbitField().get(i) && receivedbitField.get(i)){
                                
                                
                                availablePieces.add(i);

                            }
                            else{
                                //System.out.println("maybe one day/" + i);
                            }
                            forloopSize++;

                           }


                               if(!availablePieces.isEmpty()){
                              
                                Random random = new Random();
                                int randomIndex = random.nextInt(availablePieces.size());
                                int randomPiece = availablePieces.get(randomIndex);
                                if(receivedbitField.get(randomPiece) && currPeer.getbitField().get(randomPiece)){
                                    break;
                                }
                                 System.out.println("requested new at " + randomPiece);
                                 requestMessage(randomPiece);
                                 downloadRate++;
                                 total++;
                                 System.out.println("current total:" + total);
                                 break;
 
                               }
                           


                        // for(int i = 0; i < receivedbitField.length(); i++){
                        //     if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                        //         avaliablePieces.add(i);
                        //     //     System.out.println("requested new at " + i);
                        //     //    System.out.println("buffer groverflow");
                        //     //     requestMessage(i);
                        //     //     break;
   
                        //     }
                        // }
                        //  for(int i = 0; i < receivedbitField.length(); i++){
                        //     if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                        //         System.out.println("requested new at " + i);
                        //        System.out.println("buffer groverflow");
                        //         requestMessage(i);
                        //         total++;
                        //         System.out.println("current total:" + total);       
                        //         break;
   
                        //     }
                        // }
                        //    break;
                      //  Random random = new Random();
                       // int randomIndex = random.nextInt(avaliablePieces.size());
                       //  System.out.println("requested new at " + randomIndex);
                       //  requestMessage(randomIndex);
                      //   downloadRate++;
                      
                        // break;
                           // break;
       
                        
                     
                            //CODE THAT WORKS BUT ITS IN ORDER
                          // for(int i = 0; i < receivedbitField.length(); i++){
                        //     if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                        //         System.out.println("requested new at " + i);
                        //        System.out.println("buffer groverflow");
                        //         requestMessage(i);
                        //         break;
   
                        //     }
                           // break;
    



                    }
                   
                    
				}
			} catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } 
           finally
            {
                //Close connections
                //try
                //{
                    //in.close();
                    //out.close();
                    //connection.close();
                //}
                //catch(IOException ioException)
               // {
                    
                //ystem.out.println("Disconnect with Client " + no);
                //}
            }
			//catch(ClassNotFoundException classnot)
            //{
				//	System.err.println("Data received in unknown format");
			//	}
		} 
		catch(IOException ioException)
        {
			System.out.println("Disconnect with Client " + no);
        } 
	
		finally
        {
			//Close connections
			//try
            //{
				//in.close();
				//out.close();
				//connection.close();
			//}
			//catch(IOException ioException)
           // {
                
			//	System.out.println("Disconnect with Client " + no);
			//}
		}



    


	}

    public   void sendHandshake(){
        handShake newShake = new handShake(currPeer.getpeerID());
        System.out.println(currPeer.getpeerID());
        byte[] handshakeBytes = newShake.encode();
        sendMessage(handshakeBytes);

     } 
     public   void sendUnchoke(){
        byte[] dummy = new byte[20];
        Message message = new Message(MessageType.UNCHOKE,dummy);
        byte[] encodedMessage = message.encode();
        sendMessage(encodedMessage);
     } 
     public   void choke(){
        byte[] dummy = new byte[20];
        Message message = new Message(MessageType.CHOKE,dummy);
        byte[] encodedMessage = message.encode();
        sendMessage(encodedMessage);
     } 


     public void updateInterestStatus(int PeerID, boolean isInterested) {
        System.out.println("putting" + PeerID);
        peerInterestStatus.put(PeerID, isInterested);
    }
    public boolean isPeerLiked(int PeerID) {
        System.out.println(PeerID + "IN ISPEERLIKED");
        return peerInterestStatus.getOrDefault(PeerID, false);
    }
    public  void sendInterestedMessage(){
        byte[] dummy = new byte[20];
        Message message = new Message(MessageType.INTERESTED,dummy);
        byte[] encodedMessage = message.encode();
        sendMessage(encodedMessage);
        updateInterestStatus(PeerID, true);


     } public   void sendNotInterestedMessage(){
        byte[] dummy = new byte[20];
        Message message = new Message(MessageType.NOT_INTERESTED,dummy);
        byte[] encodedMessage = message.encode();
        sendMessage(encodedMessage);
        updateInterestStatus(PeerID, false);

     }
     
    public boolean isInterestedInPeer(int peerId) {
        BitSet theirBitfield = peerBitfields.get(peerId);
        BitSet ourBitfield = currPeer.getbitField();  
        BitSet interestSet = (BitSet) theirBitfield.clone();
        interestSet.andNot(ourBitfield);
        return !interestSet.isEmpty();
    }
    public    void sendMessage(byte[] msg){
        try
        {
            out.writeObject(msg);
            out.flush();
           // System.out.println("Send message: " + msg + " to Client " + no);
        }
        catch(IOException ioException)
        {
            ioException.printStackTrace();
        }
    }
    public  void requestMessage(int i ){
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(i);
        byte[] payload = buffer.array();
        Message message = new Message(MessageType.REQUEST, payload);
        byte[] toSend = message.encode();
        sendMessage(toSend);
    
     }

       public  void pieceMessage(int i, byte[] pieceRequested ){
       // System.out.println("this is the offset:" + i);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + configInfo.getpieceSize());
        buffer.putInt(i);
        buffer.put(pieceRequested);
        byte[] payload = buffer.array();
        Message message = new Message(MessageType.PIECE, payload);
        byte[] toSend = message.encode();
        sendMessage(toSend);
    
     }

     public List<Integer> selectPreferredNeighbors() {
        List<Integer> preferredNeighbors = new ArrayList<>();
        for (Integer peerId : peerInterestStatus.keySet()) {
            if (isPeerLiked(peerId)) {
                preferredNeighbors.add(peerId);
            }
        }
       //need add sort by download rate or update rate.
        return preferredNeighbors;
    }
    
    
	//send a message to the output stream


   
    }


    // same handler as before but now for the client
    private static class clientHandler extends Thread {
        private String message;    //message received from the client
		private String MESSAGE;    //uppercase message send to the client
		private Socket connection;
        private ObjectInputStream in;	//stream read from the socket
        private  ObjectOutputStream out;    //stream write to the socket
		private int no;		//The index number of the client
        public int downloadRate;
        byte[] fileInfo;
        byte[] payload;
        BitSet receivedbitField;
        static HashMap<Integer, Integer> downloadRates = new HashMap<>();
        private int PeerID;




        public clientHandler(Socket connection, int no) {
            this.connection = connection;
        this.no = no;
    }
    public void run()
         {
            try {
                System.out.println(fileName);
                RandomAccessFile fileWrite = new RandomAccessFile(fileName, "rw"); //https://stackoverflow.com/questions/22020447/write-a-number-to-fileoutputstream-after-an-offset
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
               
			//in = new ObjectInputStream(connection.getInputStream());
                    byte[] incomingHandshake = (byte[]) in.readObject();
                    handShake decoded = handShake.decode(incomingHandshake);
                    PeerID = decoded.getPeerId();
                    System.out.println("This is the header: " + decoded.getHeader());
                    System.out.println("This is the ID: " + decoded.getPeerId());
                    System.out.println("This is the zero bits length: " + decoded.getzerobitsLength());
                    handShake newShake = new handShake(currPeer.getpeerID());
                    byte[] handshakeBytes = newShake.encode();
                    out.writeObject(handshakeBytes);
                    out.flush();
            try
            {
                while(true)
				{
               // sleep(5000);
                //System.out.println("do i hit this twice or something?");
                byte[] incomingMessage = (byte[]) in.readObject();
                Message decodedMessage = Message.decode(incomingMessage); // Create an instance and then call decode
               // System.out.println("is this printing anything??");
                System.out.println(decodedMessage.getType());
               // System.out.println("is this printing anything??");

                switch(decodedMessage.getType()){
                    case CHOKE:
                    break;

                    case UNCHOKE:
                    break;

                    case INTERESTED:

                    break;

                    case NOT_INTERESTED:
                    break;


                    
                    //get have msg from peers, check the payload with our bitfield, if we have not the piece, send Interest msg, else no interest.
                    case HAVE:
                        payload = decodedMessage.getPayload();                   
                        ByteBuffer payloadBuffer = ByteBuffer.wrap(payload);
                        int pieceIndex = payloadBuffer.getInt();

                        if (!currPeer.getbitField().get(pieceIndex)) {
                            
                            sendInterestedMessage();

                        } else {
                            
                            sendNotInterestedMessage();
                        }
                        break;

                    case BITFIELD:
                    System.out.println("get bitfiled msg");

                            receivedbitField = BitSet.valueOf(decodedMessage.getPayload());
                           // BitSet bitfield = BitSet.valueOf(payload);
                            peerBitfields.put(PeerID, receivedbitField);
                            

                            if(isInterestedInPeer(PeerID)){
                                sendInterestedMessage();
                            }
                            else{
                                sendNotInterestedMessage();
                            }
                            break;
                  //  receivedbitField = BitSet.valueOf(decodedMessage.getPayload());
                    //System.out.println("this is the length: " + receivedbitField.length());
                    // for(int i = 0; i < receivedbitField.length(); i++){
                    //     if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                    //       //  System.out.println("found at value" + i);
                    //        // System.out.println("buffer groverflow");
                    //         requestMessage(i);
                    //         break;

                    //     }
    
                    // }
                    //break;
                    case PIECE:
                      //  System.out.println("I MADE IT TO PIECE");
                        payload = decodedMessage.getPayload();
                        ByteBuffer buffer = ByteBuffer.wrap(payload);
                        int offset = buffer.getInt(); // Extract the offset from the first 4 bytes
                        int fileOffset = offset * configInfo.getpieceSize();
                        System.arraycopy(payload, 4, fileData, fileOffset, configInfo.getpieceSize()); 
                        fileWrite.seek(fileOffset);
                        fileWrite.write(payload, 4, configInfo.getpieceSize());
                        currPeer.getbitField().set(offset);
                         for(int i = 0; i < receivedbitField.length(); i++){
                             if(receivedbitField.get(i) && !currPeer.getbitField().get(i)){
                                 System.out.println("requested new at " + i);
                                System.out.println("buffer groverflow");
                                 requestMessage(i);
                                 break;
    
                             }
        
                         }
                        downloadRate++;
                        
                        
                        //int filewritePosition = 
                      //  System.out.println(fileData.length);
                       // fileWrite.seek()


                    
                  // System.arraycopy(decodedMessage.getPayload(), offset, requestedPiece, 0, configInfo.getpieceSize());
                    


                }
                // System.out.println(decodedMessage.getType());
                // byte[] imageMaybe = decodedMessage.getPayload();
                //   String tempPath = "peer_1002/test.jpg";
                //      FileOutputStream fileOutputStream = new FileOutputStream(tempPath);
                //     fileOutputStream.write(decodedMessage.getPayload());
                //      fileOutputStream.close();
                //      System.out.println("do i make it here");

                

				
                    
                    

					
                   
                    //TEST FOR SENDING IMAGE, WORKING 
                    // byte[] test = (byte[]) in.readObject();
                     //System.arraycopy(test, 0, fileData, 16384);
                    //fileData = 
                    // out.writeObject(test);
                    // String tempPath = "peer_1002/test.jpg";
                    // FileOutputStream fileOutputStream = new FileOutputStream(tempPath);
                    // fileOutputStream.write(test);
                    // fileOutputStream.close();

                    
				}
			
            } finally {
                //System.out.println("test");
            }
        }
            catch(ClassNotFoundException classnot)
            {
					System.err.println("Data received in unknown format");
				}
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            
          
            } 
            public  void sendMessage(byte[] msg)
            {
                try
                {
                    out.writeObject(msg);
                    out.flush();
                   // System.out.println("Send message: " + msg + " to Client " + no);
                }
                catch(IOException ioException)
                {
                    ioException.printStackTrace();
                }
            }

            public  void requestMessage(int i ){
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
                buffer.putInt(i);
                byte[] payload = buffer.array();
                Message message = new Message(MessageType.REQUEST, payload);
                byte[] toSend = message.encode();
                sendMessage(toSend);
            
             }
             public boolean isInterestedInPeer(int peerId) {
                BitSet theirBitfield = peerBitfields.get(peerId);
                BitSet ourBitfield = currPeer.getbitField();  
                BitSet interestSet = (BitSet) theirBitfield.clone();
                interestSet.andNot(ourBitfield);
                return !interestSet.isEmpty();
            }

               public  void pieceMessage(int i, byte[] pieceRequested ){
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + configInfo.getpieceSize());
                buffer.putInt(i);
                byte[] payload = buffer.array();
                Message message = new Message(MessageType.REQUEST, payload);
                byte[] toSend = message.encode();
                sendMessage(toSend);
            
             }
             public  void sendInterestedMessage(){
                byte[] dummy = new byte[20];
                Message message = new Message(MessageType.INTERESTED,dummy);
                byte[] encodedMessage = message.encode();
                System.out.println("message sent!");
                sendMessage(encodedMessage);

             } public  void sendNotInterestedMessage(){
                Message message = new Message(MessageType.NOT_INTERESTED,null);
                byte[] encodedMessage = message.encode();
                sendMessage(encodedMessage);

             }
             

         }
   

       



       
         public static void loadFile(String folderName) {
            try {
                File folder = new File(folderName);
                File[] allFiles = folder.listFiles();
                File sendFile = allFiles[1];
               // System.out.println("Getting the name:" + sendFile.getName());
                if (sendFile == null) {
                    System.out.println("File not found.");
                }
                FileInputStream in = new FileInputStream(sendFile);
                fileData = new byte[(int) sendFile.length()];
                if(fileData.length == sendFile.length()){
                    System.out.println("im supposed to be here");

                }
                in.read(fileData);
                in.close();
                System.out.println("File loaded successfully for peer " + currPeer.getpeerID());
            } catch (IOException e) {
                System.err.println("Error loading file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    




        
