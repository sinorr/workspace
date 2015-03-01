package jpolylda;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

public class Model {	
	
	//---------------------------------------------------------------
	//	Class Variables
	//---------------------------------------------------------------
	
	public static String tassignSuffix;	//suffix for topic assignment file
	public static String thetaSuffix;		//suffix for theta (topic - document distribution) file
	public static String phiSuffix;		//suffix for phi file (topic - word distribution) file
	public static String othersSuffix; 	//suffix for containing other parameters
	public static String twordsSuffix;		//suffix for file containing words-per-topics
	
	//---------------------------------------------------------------
	//	Model Parameters and Variables
	//---------------------------------------------------------------
	
	public String wordMapFile; 		//file that contain word to id map
	public String trainlogFile; 	//training log file	
	
	public String dir;
	public String dfile;
	public String modelName;
	public int modelStatus; 		//see Constants class for status of model
	public LDADataset data[];			// link to a dataset
	public String langNames[];         //language suffixs of languages
	
	public int L; //number of languages
	public int M; //dataset size (i.e., number of docs)
	public int V[]; //vocabulary size
	public int K; //number of topics
	public double alpha, beta; //LDA  hyperparameters
	public int niters; //number of Gibbs sampling iteration
	public int liter; //the iteration at which the model was saved	
	public int savestep; //saving period
	public int twords; //print out top words per each topic
	public int withrawdata;
	
	// Estimated/Inferenced parameters
	public double [][] theta; //theta: document - topic distributions, size M x K
	public double [][][] phi; // phi: topic-word distributions, size L X K x V
	
	// Temp variables while sampling
	public Vector<Integer> [][] z; //topic assignments for words, size M x doc.size()
	protected int [][][] nw; //nw[i][j]: number of instances of word/term i assigned to topic j, size V x K
	protected int [][] nd; //nd[i][j]: number of words in document i assigned to topic j, size M x K
	protected int [][] nwsum; //nwsum[j]: total number of words assigned to topic j, size K
	protected int [] ndsum; //ndsum[i]: total number of words in document i, size M
	
	// temp variables for sampling
	protected double [][] p; 
	
	//---------------------------------------------------------------
	//	Constructors
	//---------------------------------------------------------------	

	public Model(){
		setDefaultValues();	
	}
	
	/**
	 * Set default values for variables
	 */
	public void setDefaultValues(){
		wordMapFile = "wordmap.txt";
		trainlogFile = "trainlog.txt";
		tassignSuffix = ".tassign";
		thetaSuffix = ".theta";
		phiSuffix = ".phi";
		othersSuffix = ".others";
		twordsSuffix = ".twords";
		
		dir = "./";
		dfile = "trndocs.dat";
		modelName = "model-final";
		modelStatus = Constants.MODEL_STATUS_UNKNOWN;		
		
		L = 1;
		M = 0;
		V = null;
		K = 100;
		alpha = 50.0 / K;
		beta = 0.1;
		niters = 2000;
		liter = 0;
		
		langNames = new String[L];
		z = null;
		nw = null;
		nd = null;
		nwsum = null;
		ndsum = null;
		theta = null;
		phi = null;
	}
	
	//---------------------------------------------------------------
	//	I/O Methods
	//---------------------------------------------------------------
	/**
	 * read other file to get parameters
	 
	protected boolean readOthersFile(String otherFile){
		//open file <model>.others to read:
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(otherFile));
			String line;
			while((line = reader.readLine()) != null){
				StringTokenizer tknr = new StringTokenizer(line,"= \t\r\n");
				
				int count = tknr.countTokens();
				if (count != 2)
					continue;
				
				String optstr = tknr.nextToken();
				String optval = tknr.nextToken();
				
				if (optstr.equalsIgnoreCase("alpha")){
					alpha = Double.parseDouble(optval);					
				}
				else if (optstr.equalsIgnoreCase("beta")){
					beta = Double.parseDouble(optval);
				}
				else if (optstr.equalsIgnoreCase("ntopics")){
					K = Integer.parseInt(optval);
				}
				else if (optstr.equalsIgnoreCase("liter")){
					liter = Integer.parseInt(optval);
				}
				else if (optstr.equalsIgnoreCase("nwords")){
					V = Integer.parseInt(optval);
				}
				else if (optstr.equalsIgnoreCase("ndocs")){
					M = Integer.parseInt(optval);
				}
				else {
					// any more?
				}
			}
			
			reader.close();
		}
		catch (Exception e){
			System.out.println("Error while reading other file:" + e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	protected boolean readTAssignFile(String tassignFile){
		try {
			int i,j;
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(tassignFile), "UTF-8"));
			
			String line;
			z = new Vector[M];			
			data = new LDADataset(M);
			data.V = V;			
			for (i = 0; i < M; i++){
				line = reader.readLine();
				StringTokenizer tknr = new StringTokenizer(line, " \t\r\n");
				
				int length = tknr.countTokens();
				
				Vector<Integer> words = new Vector<Integer>();
				Vector<Integer> topics = new Vector<Integer>();
				
				for (j = 0; j < length; j++){
					String token = tknr.nextToken();
					
					StringTokenizer tknr2 = new StringTokenizer(token, ":");
					if (tknr2.countTokens() != 2){
						System.out.println("Invalid word-topic assignment line\n");
						return false;
					}
					
					words.add(Integer.parseInt(tknr2.nextToken()));
					topics.add(Integer.parseInt(tknr2.nextToken()));
				}//end for each topic assignment
				
				//allocate and add new document to the corpus
				Document doc = new Document(words);
				data.setDoc(doc, i);
				
				//assign values for z
				z[i] = new Vector<Integer>();
				for (j = 0; j < topics.size(); j++){
					z[i].add(topics.get(j));
				}
				
			}//end for each doc
			
			reader.close();
		}
		catch (Exception e){
			System.out.println("Error while loading model: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * load saved model
	 
	public boolean loadModel(){
		if (!readOthersFile(dir + File.separator + modelName + othersSuffix))
			return false;
		
		if (!readTAssignFile(dir + File.separator + modelName + tassignSuffix))
			return false;
		
		// read dictionary
		Dictionary dict = new Dictionary();
		if (!dict.readWordMap(dir + File.separator + wordMapFile))
			return false;
			
		data.localDict = dict;
		
		return true;
	}
	
	/**
	 * Save word-topic assignments for this model
	 */
	public boolean saveModelTAssign(String filename){
		int i, j, l;
		
		for (l = 0; l < L; l++) {
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(
						filename + '.' + langNames[l]));

				// write docs with topic assignments for words

				for (i = 0; i < M; i++) {
					for (j = 0; j < data[l].docs[i].length; ++j) {
						writer.write(data[l].docs[i].words[j] + ":"
								+ z[l][i].get(j) + " ");
					}
					writer.write("\n");
				}

				writer.close();
			}

			catch (Exception e) {
				System.out.println("Error while saving model tassign: "
						+ e.getMessage());
				e.printStackTrace();
				return false;
			}
		} //end for each language
		return true;
	}
	
	/**
	 * Save theta (topic distribution) for this model
	 */
	public boolean saveModelTheta(String filename){
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			for (int i = 0; i < M; i++){
				for (int j = 0; j < K; j++){
					writer.write(theta[i][j] + " ");
				}
				writer.write("\n");
			}
			writer.close();
		}
		catch (Exception e){
			System.out.println("Error while saving topic distribution file for this model: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Save word-topic distribution
	 */
	
	public boolean saveModelPhi(String filename){
		for (int l = 0; l < L; l++) {
			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(
						filename + '.' + langNames[l]));

				for (int i = 0; i < K; i++) {
					for (int j = 0; j < V[l]; j++) {
						writer.write(phi[l][i][j] + " ");
					}
					writer.write("\n");
				}
				writer.close();
			} catch (Exception e) {
				System.out
						.println("Error while saving word-topic distribution:"
								+ e.getMessage());
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Save other information of this model
	 */
	public boolean saveModelOthers(String filename){
		try{
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			
			writer.write("alpha=" + alpha + "\n");
			writer.write("beta=" + beta + "\n");
			writer.write("ntopics=" + K + "\n");
			writer.write("ndocs=" + M + "\n");
			writer.write("nwords=" + V + "\n");
			writer.write("liters=" + liter + "\n");
			writer.write("nlanguages=" + L + "\n");
			writer.write("names of languages: ");
			for( int l = 0; l < L; l++ ){
				writer.write(langNames[l] + ' ');
			}
			writer.write("\n");
			
			writer.close();
		}
		catch(Exception e){
			System.out.println("Error while saving model others:" + e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Save model the most likely words for each topic
	*/
	public boolean saveModelTwords(String filename) {

		for (int l = 0; l < L; l++) {
			try {
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(filename
								+ '.' + langNames[l]), "UTF-8"));

				if (twords > V[l]) {
					twords = V[l];
				}

				for (int k = 0; k < K; k++) {
					List<Pair> wordsProbsList = new ArrayList<Pair>();
					for (int w = 0; w < V[l]; w++) {
						Pair p = new Pair(w, phi[l][k][w], false);

						wordsProbsList.add(p);
					}// end foreach word

					// print topic
					writer.write("Topic " + k + "th:\n");
					Collections.sort(wordsProbsList);

					for (int i = 0; i < twords; i++) {
						if (data[l].localDict.contains((Integer) wordsProbsList
								.get(i).first)) {
							String word = data[l].localDict
									.getWord((Integer) wordsProbsList.get(i).first);

							writer.write("\t" + word + " "
									+ wordsProbsList.get(i).second + "\n");
						}
					}
				} // end for each topic

				writer.close();
			} catch (Exception e) {
				System.out.println("Error while saving model twords: "
						+ e.getMessage());
				e.printStackTrace();
				return false;
			}
		} // end for each language
		return true;
	}
	
	/**
	 * List several language words in one line for each topic
	 */
	public boolean listTwords(String filename) {
			
		List<Pair> wordsProbsList[] = new ArrayList[L];
			try {
				BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(filename), "UTF-8"));
			for (int l = 0; l < L; l++) {
				if (twords > V[l]) {
					twords = V[l];
				}
			
				for (int k = 0; k < K; k++) {
					wordsProbsList[l] = new ArrayList<Pair>();
					for (int w = 0; w < V[l]; w++) {
						Pair p = new Pair(w, phi[l][k][w], false);

						wordsProbsList[l].add(p);
					}// end foreach word
				}
				Collections.sort(wordsProbsList[l]);
			}//end for each language
				
					// print topic
			for( int k = 0; k < K; k++){
					writer.write("Topic " + k + "th:\n");

					for (int i = 0; i < twords; i++) {
						for (int l = 0; l < L; l++ ){
						if (data[l].localDict.contains((Integer) wordsProbsList[l]
								.get(i).first)) {
							String word = data[l].localDict
									.getWord((Integer) wordsProbsList[l].get(i).first);

							writer.write("\t\t" + word );
						}
					}
					writer.write("\n");	
				} // end for each language

			} // end for each topic
				writer.close();
			} catch (Exception e) {
				System.out.println("Error while saving model twords: "
						+ e.getMessage());
				e.printStackTrace();
				return false;
			}
		return true;
	}
	/**
	 * Save model
	 */
	public boolean saveModel(String modelName){
		if (!saveModelTAssign(dir + File.separator + modelName + tassignSuffix)){
			return false;
		}
		
		if (!saveModelOthers(dir + File.separator + modelName + othersSuffix)){			
			return false;
		}
		
		if (!saveModelTheta(dir + File.separator + modelName + thetaSuffix)){
			return false;
		}
		
		if (!saveModelPhi(dir + File.separator + modelName + phiSuffix)){
			return false;
		}
		
		if (twords > 0){
			if (!saveModelTwords(dir + File.separator + modelName + twordsSuffix))
				return false;
		}
		
		if (twords > 0){
			if (!listTwords(dir + File.separator + modelName + twordsSuffix))
				return false;
		}
		return true;
	}
	
	//---------------------------------------------------------------
	//	Init Methods
	//---------------------------------------------------------------
	/**
	 * initialize the model
	 */
	protected boolean init(LDACmdOption option){		
		if (option == null)
			return false;
		
		modelName = option.modelName;
		K = option.K;
		L = option.L;
		
		langNames = new String[L];
		String[] tempLangNames = option.langNames.split("&");
		if(tempLangNames.length != L ){
			System.out.println("Please enter the right languages !");
			return false;
		}
		
		for( int i = 0; i < L; i++ ){
			langNames[i] = tempLangNames[i];
		}
		
		alpha = option.alpha;
		if (alpha < 0.0)
			alpha = 50.0 / K;
		
		if (option.beta >= 0)
			beta = option.beta;
		
		niters = option.niters;
		
		dir = option.dir;
		if (dir.endsWith(File.separator))
			dir = dir.substring(0, dir.length() - 1);
		
		dfile = option.dfile;
		twords = option.twords;
		wordMapFile = option.wordMapFileName;
		
		return true;
	}
	
	/**
	 * Init parameters for estimation
	 */
	public boolean initNewModel(LDACmdOption option){
		if (!init(option))
			return false;
		
		int l, m, n, w, k;		
		p = new double[L][K];		
		
		data = new LDADataset[L];
		for (l = 0; l < L; l++) {
			data[l] = LDADataset.readDataSet(dir + File.separator + dfile + '.'
					+ langNames[l]);

			if (data[l] == null) {
				System.out.println("Fail to read training data:\n");
				System.out.println(dir + File.separator + dfile + '.'+ langNames[l]);
				return false;
			}
		}
		
		//+ allocate memory and assign values for variables		
		M = data[0].M;
		V = new int[L];
		for( l = 0; l < L; l++ ){
			V[l] = data[l].V;			
		}

		dir = option.dir;
		savestep = option.savestep;
		
		// K: from command line or default value
	    // alpha, beta: from command line or default values
	    // niters, savestep: from command line or default values

		nw = new int[L][][];
		for (l = 0; l < L; l++) {
			nw[l] = new int[V[l]][K];
			for (w = 0; w < V[l]; w++) {
				for (k = 0; k < K; k++) {
					nw[l][w][k] = 0;
				}
			}
		}
		nd = new int[M][K];
		for (m = 0; m < M; m++){
			for (k = 0; k < K; k++){
				nd[m][k] = 0;
			}
		}
		
		nwsum = new int[L][K];
		for (l = 0; l < L; l++) {
			for (k = 0; k < K; k++) {
				nwsum[l][k] = 0;
			}
		}
		
		ndsum = new int[M];
		for (m = 0; m < M; m++){
			ndsum[m] = 0;
		}
		
		z = new Vector[L][M];
		for (l = 0; l < L; l++) {
			for (m = 0; m < data[0].M; m++) {
				int N = data[l].docs[m].length;
				z[l][m] = new Vector<Integer>();

				// initilize for z
				for (n = 0; n < N; n++) {
					int topic = (int) Math.floor(Math.random() * K);
					z[l][m].add(topic);

					// number of instances of word assigned to topic j
					nw[l][data[l].docs[m].words[n]][topic] += 1;
					// number of words in document i assigned to topic j
					nd[m][topic] += 1;
					// total number of words assigned to topic j
					nwsum[l][topic] += 1;
				}
				// total number of words in document i
				ndsum[m] = N;
			}
		}
		theta = new double[M][K];		
		phi = new double[L][K][];
		for(l = 0; l < L; l++){
			for(k = 0; k < K; k++ ){
				phi[l][k] = new double[V[l]];
			}
		}
		
		return true;
	}
	
	/**
	 * Init parameters for inference
	 * @param newData DataSet for which we do inference
	 
	public boolean initNewModel(LDACmdOption option, LDADataset newData, Model trnModel){
		if (!init(option))
			return false;
		
		int m, n, w, k;
		
		K = trnModel.K;
		alpha = trnModel.alpha;
		beta = trnModel.beta;		
		
		p = new double[K];
		System.out.println("K:" + K);
		
		data = newData;
		
		//+ allocate memory and assign values for variables		
		M = data.M;
		V = data.V;
		dir = option.dir;
		savestep = option.savestep;
		System.out.println("M:" + M);
		System.out.println("V:" + V);
		
		// K: from command line or default value
	    // alpha, beta: from command line or default values
	    // niters, savestep: from command line or default values

		nw = new int[V][K];
		for (w = 0; w < V; w++){
			for (k = 0; k < K; k++){
				nw[w][k] = 0;
			}
		}
		
		nd = new int[M][K];
		for (m = 0; m < M; m++){
			for (k = 0; k < K; k++){
				nd[m][k] = 0;
			}
		}
		
		nwsum = new int[K];
		for (k = 0; k < K; k++){
			nwsum[k] = 0;
		}
		
		ndsum = new int[M];
		for (m = 0; m < M; m++){
			ndsum[m] = 0;
		}
		
		z = new Vector[M];
		for (m = 0; m < data.M; m++){
			int N = data.docs[m].length;
			z[m] = new Vector<Integer>();
			
			//initilize for z
			for (n = 0; n < N; n++){
				int topic = (int)Math.floor(Math.random() * K);
				z[m].add(topic);
				
				// number of instances of word assigned to topic j
				nw[data.docs[m].words[n]][topic] += 1;
				// number of words in document i assigned to topic j
				nd[m][topic] += 1;
				// total number of words assigned to topic j
				nwsum[topic] += 1;
			}
			// total number of words in document i
			ndsum[m] = N;
		}
		
		theta = new double[M][K];		
		phi = new double[K][V];
		
		return true;
	}
	
	/**
	 * Init parameters for inference
	 * reading new dataset from file
	 
	public boolean initNewModel(LDACmdOption option, Model trnModel){
		if (!init(option))
			return false;
		
		LDADataset dataset = LDADataset.readDataSet(dir + File.separator + dfile, trnModel.data.localDict);
		if (dataset == null){
			System.out.println("Fail to read dataset!\n");
			return false;
		}
		
		return initNewModel(option, dataset , trnModel);
	}
	
	/**
	 * init parameter for continue estimating or for later inference
	 
	public boolean initEstimatedModel(LDACmdOption option){
		if (!init(option))
			return false;
		
		int m, n, w, k;
		
		p = new double[K];
		
		// load model, i.e., read z and trndata
		if (!loadModel()){
			System.out.println("Fail to load word-topic assignment file of the model!\n");
			return false;
		}
		
		System.out.println("Model loaded:");
		System.out.println("\talpha:" + alpha);
		System.out.println("\tbeta:" + beta);
		System.out.println("\tM:" + M);
		System.out.println("\tV:" + V);		
		
		nw = new int[V][K];
		for (w = 0; w < V; w++){
			for (k = 0; k < K; k++){
				nw[w][k] = 0;
			}
		}
		
		nd = new int[M][K];
		for (m = 0; m < M; m++){
			for (k = 0; k < K; k++){
				nd[m][k] = 0;
			}
		}
		
		nwsum = new int[K];
	    for (k = 0; k < K; k++) {
		nwsum[k] = 0;
	    }
	    
	    ndsum = new int[M];
	    for (m = 0; m < M; m++) {
		ndsum[m] = 0;
	    }
	    
	    for (m = 0; m < data.M; m++){
	    	int N = data.docs[m].length;
	    	
	    	// assign values for nw, nd, nwsum, and ndsum
	    	for (n = 0; n < N; n++){
	    		w = data.docs[m].words[n];
	    		int topic = (Integer)z[m].get(n);
	    		
	    		// number of instances of word i assigned to topic j
	    		nw[w][topic] += 1;
	    		// number of words in document i assigned to topic j
	    		nd[m][topic] += 1;
	    		// total number of words assigned to topic j
	    		nwsum[topic] += 1;	    		
	    	}
	    	// total number of words in document i
	    	ndsum[m] = N;
	    }
	    
	    theta = new double[M][K];
	    phi = new double[K][V];
	    dir = option.dir;
		savestep = option.savestep;
	    
		return true;
	}
	*/
}
