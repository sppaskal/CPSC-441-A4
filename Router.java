import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;

/**
 * Router Class
 * 
 * Router implements Dijkstra's algorithm for computing the minumum distance to all nodes in the network
 * @author      XYZ
 * @version     1.0
 *
 */
public class Router {

 	/**
     	* Constructor to initialize the program 
     	* 
     	* @param peerip		IP address of other routers (we assume that all routers are running in the same machine)
     	* @param routerid	Router ID
     	* @param port		Router UDP port number
     	* @param configfile	Configuration file name
     	* @param neighborupdate	link state update interval - used to update router's link state vector to neighboring nodes
        * @param routeupdate 	Route update interval - used to update route information using Dijkstra's algorithm
 
     */
	private String peerIP;
	private int routerID;
	private int port;
	private String configFile;
	private int neighborUpdate;
	private int routeUpdate;
	private InetAddress ip;
	private ArrayList<String> neighbors = new ArrayList<String>();
	private ArrayList <int[]> dataStructure = new ArrayList<int[]>();
	private ArrayList <int[]> finalArrayList = new ArrayList<int[]>();
	private FileInputStream inputStream;
	private BufferedReader bReader;
	private String line;
	private String label;
	private LinkState linkState;
	private int numOfNeighbors;
	private Timer timer;
	private int [] linkStateVectors;
	private DatagramSocket socket = null;
	private int dataCounter = 0;
	boolean test = true;
	private int iteration = 0;
	
	public Router(String peerip, int routerid, int port, String configfile, int neighborupdate, int routeupdate) throws UnknownHostException {
		this.peerIP = peerip;
		this.routerID = routerid;
		this.port = port;
		this.configFile = configfile;
		this.neighborUpdate = neighborupdate;
		this.routeUpdate = routeupdate; 
		this.ip = InetAddress.getByName(peerIP);
		
	}
	
    	/**
     	*  Compute route information based on Dijkstra's algorithm and print the same
    	 * @throws IOException 
     	* 
     	*/
	public void compute() throws IOException {
		
		File file = new File(this.configFile);
		
		if(file.exists()){
			
			byte[] array = new byte[linkState.MAX_SIZE];
			socket = new DatagramSocket(port, ip);
			
			timer = new Timer();
			findNeighborInfo();
			linkStateVector();
			processUpdateNeighbor();
			initFinalArrayList();
			processUpdateRoute();
			
			DatagramPacket packet = new DatagramPacket(array, array.length);
			
			while(true){
				socket.receive(packet);
				processUpDateDS(packet);
			}
		}
		else{
			System.out.println("The given file does not exist...");
		}
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//

	public synchronized void processUpDateDS(DatagramPacket receivePacket) throws IOException{
		
		LinkState linkState = new LinkState(receivePacket);
		int numOfNodes = Integer.parseInt(neighbors.get(0));
		int [] vectorUpdate = new int[numOfNodes];
		vectorUpdate = linkState.getCost();
		
		int routNum = Integer.parseInt(neighbors.get(0)); //Number of routers
		
		boolean isUnique = true;
		for(int q = 0; q < dataStructure.size(); q++){
			if(Arrays.equals(dataStructure.get(q), vectorUpdate)){
				isUnique = false;
			}
		}
		
		if(dataCounter < routNum && isUnique){
			dataStructure.add(vectorUpdate);
			dataCounter++;
			
			//*********************************************************************************************************************//
			//Uncomment code below to see router receiving broadcasted information from neighbours and updating its datastructure.
			//*********************************************************************************************************************//
			/*System.out.println("*******************");
			System.out.println("DataStructure: ");
			System.out.println("*******************");
			for(int j = 0; j < dataStructure.size(); j++){
				System.out.println("Array: " + j);
				for(int k = 0; k < dataStructure.get(j).length; k++){
					System.out.println(dataStructure.get(j)[k] + " ");
				}
				System.out.println("-------------------");
			}
			*/
		}
		
		//This should broadcast the vector array we just received to all of its neighbors.
		numOfNeighbors = neighbors.size();
		for(int i = 1; i < neighbors.size(); i++){
	
			String neighbor = neighbors.get(i);
			String [] neighborParts = neighbor.split(" ");
			String neighborPort = neighborParts[3];
			int neighborPortNum = Integer.parseInt(neighborPort);
			int destinationID = Integer.parseInt(neighborParts[1]);
			
			linkState = new LinkState(routerID, destinationID, vectorUpdate);
			
			DatagramPacket packet = new DatagramPacket(linkState.getBytes(), linkState.getBytes().length, ip, neighborPortNum);

			socket.send(packet);
		}
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//
	
	public synchronized void processUpdateNeighbor() throws IOException{

		//We want to run this while loop every 1000 ms. It will send a update to all its neighbors.
		numOfNeighbors = neighbors.size();
		for(int i = 1; i < neighbors.size(); i++){
	
			String neighbor = neighbors.get(i);
			String [] neighborParts = neighbor.split(" ");
			String neighborPort = neighborParts[3];
			int neighborPortNum = Integer.parseInt(neighborPort);
			int destinationID = Integer.parseInt(neighborParts[1]);
			
			linkState = new LinkState(routerID, destinationID, linkStateVectors);
			
			//*********************************************************************************************************************//
			//Uncomment next line to see router sending state vectors to neighbors.
			//*********************************************************************************************************************//
			//System.out.println("Sending to ID/Port: " + destinationID + "/" + neighborPortNum + " LinkStateVector: " + "[" + linkStateVectors[0] + ", " + linkStateVectors[1] + ", " + linkStateVectors[2] + ", " + linkStateVectors[3] + ", " + linkStateVectors[4] + "]");
			
			DatagramPacket packet = new DatagramPacket(linkState.getBytes(), linkState.getBytes().length, ip, neighborPortNum);

			socket.send(packet);
		}
		
		
		SendStateHandler ssh = new SendStateHandler(this);
		timer.schedule(ssh, 1000);	
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//
	
	public synchronized void processUpdateRoute(){
	
		int numOfNodes = Integer.parseInt(neighbors.get(0));

		//We go through our data structure and use every vector array out our disposal to see if we can find a shorter path.
		for(int count = 0; count < dataStructure.size(); count++){
			int [] vectorUpdate = new int[numOfNodes];
			vectorUpdate = dataStructure.get(count);
			
			//We want to know the ID of the node to which the updateNode belongs to incase we end up using one of its vectors.
			int updateNodeID = 0;
			for(int j = 0; j < vectorUpdate.length; j++){
				if(vectorUpdate[j] == 0){
					updateNodeID = j;
				}
			}
			
			//Here we check every element of current vector array to every element of update vector array.
			for(int i = 0; i < vectorUpdate.length; i++){
				int[] array = new int[2];
				if(vectorUpdate[i] + linkStateVectors[updateNodeID] < linkStateVectors[i] && vectorUpdate[i] != 0){
					linkStateVectors[i] = vectorUpdate[i] + linkStateVectors[updateNodeID];
					array[0] = vectorUpdate[i] + linkStateVectors[updateNodeID];
					array[1] = updateNodeID;
					finalArrayList.set(i, array);
				}
			}
		}	
		
		UpdateVectorHandler uvh = new UpdateVectorHandler(this);
		timer.schedule(uvh, 10000);	
		
		/**** You may use the follwing piece of code to print routing table info *******/
    	System.out.println("Routing Info");
    	System.out.println("RouterID \t Distance \t Prev RouterID");
    	for(int i = 0; i < numOfNodes; i++)
      	{
      		System.out.println(i + "\t\t   " + finalArrayList.get(i)[0] +  "\t\t\t" +  finalArrayList.get(i)[1]);
      	}
    	iteration++;
    	System.out.println("----------------------------------------------");
    	System.out.println("Calculating next iteration (" + iteration + ")");
    	System.out.println("----------------------------------------------");
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//
	
	//This will setup initial finalArrayList before we update it with vectors broadcasted by other routers.
	public void initFinalArrayList(){
		for(int elem = 0; elem < linkStateVectors.length; elem++){
			int[] tempArray = new int[2];
			tempArray[0] = linkStateVectors[elem];
			tempArray[1] = routerID;
			finalArrayList.add(elem, tempArray);
		}
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//
	
	//Makes an array of all neighbors
	public void findNeighborInfo() throws IOException{
		inputStream = new FileInputStream(configFile);
		bReader = new BufferedReader(new InputStreamReader(inputStream));
		
		//Read the configfile line by line. 
		while((line = bReader.readLine()) != null) {
			//Populate the neighbors arraylist.
			neighbors.add(line);
		}
	}
	
	//----------------------------------------------------------------------------------------------------------------------------//
	
	//Makes an array of linkstate vectors
	public void linkStateVector() throws IOException{

		int numOfNodes = Integer.parseInt(neighbors.get(0));
		linkStateVectors = new int[numOfNodes];
		for(int k = 0; k < numOfNodes; k++){
			linkStateVectors[k] = 999;
		}
		
		int i = 0;
		int count = 1;
		linkStateVectors[routerID] = 0;
		while(count < neighbors.size()){
			
			String [] parts = neighbors.get(count).split(" ");
			int curNeighborID = Integer.parseInt(parts[1]);
			int curNeighborCost = Integer.parseInt(parts[2]);
			
			if(curNeighborID == i){
				linkStateVectors[i] = curNeighborCost;
				count++;
			}
			
			i++;	
		}
	}

	//----------------------------------------------------------------------------------------------------------------------------//
	
	public static void main(String[] args) throws IOException {
		
		String peerip = "127.0.0.1"; // all router programs running in the same machine for simplicity
		String configfile = "";
		int routerid = 999;
        int neighborupdate = 1000; // milli-seconds, update neighbor with link state vector every second
		int forwardtable = 10000; // milli-seconds, print route information every 10 seconds
		int port = -1; // router port number
	
		// check for command line arguments
		if (args.length == 3) {
			// either provide 3 parameters
			routerid = Integer.parseInt(args[0]);
			port = Integer.parseInt(args[1]);	
			configfile = args[2];
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java Router routerid routerport configfile");
			System.exit(0);
		}

		
		Router router = new Router(peerip, routerid, port, configfile, neighborupdate, forwardtable);
		
		System.out.println("Router initialized..running");
		router.compute();
	}

}
