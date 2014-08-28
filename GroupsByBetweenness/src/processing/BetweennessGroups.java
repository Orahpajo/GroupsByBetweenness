package processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import data.Edges;
import data.Nodes;
import dataProcessing.SQLGrabber;

public class BetweennessGroups {
	static List<Nodes> nodeList;
	static List<Edges> edgeList;
	static boolean debug = false;
	
	public static void main(String[] args){
		//GDFReader.GDFtoSQL("C:\\Users\\Marvin\\Desktop\\MarvsFriendNetwork.gdf");
		nodeList=SQLGrabber.grabNodes();
		edgeList=SQLGrabber.grabEdges();	
		
		List<Map<String,List<Nodes>>> tyler=tyler(10,Double.POSITIVE_INFINITY);
		SQLGrabber.saveSets(tyler, false);
		//if (debug) 
			System.out.println("Terminated");
	}
	/**
	 * Repeatedly uses findBetwCommunities to get different sets of communities. Then names matching communities
	 * with the same name
	 * @param numberOfSets Number Of Sets to produce
	 * @param threshold Threshold for the betweenness for the adjusted brandes
	 * @return set of communities
	 */
	public static List<Map<String,List<Nodes>>> tyler(int numberOfSets,double threshold){
		List<Map<String,List<Nodes>>> sets = new ArrayList<Map<String,List<Nodes>>>();
		for (int i =0;i<numberOfSets;i++){
			sets.add(findBetwCommunities(threshold)); System.out.println("Iteration "+i+1);	
			assignNeighbors();					
		}
		//Give matching communities the same name
		for (int i = 0 ; i<sets.size()-1;i++){
			Map<String,List<Nodes>> set = sets.get(i);
			Map<String,List<Nodes>> nextSet = sets.get(i+1);
			//communities in the current set
			for (Map.Entry<String, List<Nodes>> entry : set.entrySet()){
				if (entry.getValue()==null) continue;
				//communities in the next set
				for (Map.Entry<String, List<Nodes>> nextEntry : nextSet.entrySet()){
					if (nextEntry.getValue()==null) continue;
					List<Nodes> shared = null;
					boolean same = false; //if they are the same sets
					if (entry.getValue().size()>nextEntry.getValue().size()){
						shared = new ArrayList<Nodes>(entry.getValue());
						shared.retainAll(nextEntry.getValue());
						if (shared.size()>=Math.ceil(entry.getValue().size()/2.)) same = true;
					} else {
						shared = new ArrayList<Nodes>(nextEntry.getValue());
						shared.retainAll(entry.getValue());
						if (shared.size()>=Math.ceil(nextEntry.getValue().size()/2.)) same = true;
					}
					
					//When they are the same set rename second set to the first set 
					if (same ){
						if(!entry.getKey().equals(nextEntry.getKey())){
							String tmpString = entry.getKey();//group name
							//save list that occupies groupname space
							List<Nodes> tmpList = nextSet.get(tmpString);
							//remove group occupying space
							nextSet.remove(tmpString);
							//Put nextEntry in right space
							nextSet.put(tmpString, nextEntry.getValue());
							//save old position of nextEntry
							tmpString = nextEntry.getKey();
							//remove nextEntry from wrong space
							nextSet.remove(tmpString);
							//put saved entry onto old position from nextentry												
							nextSet.put(tmpString, tmpList);
						}
						//Break inner for loop as similar group is found
						break;
					}					
				}
				
			}
		}
		return sets;
	}
	
	/**
	 * Finds communities based on the algorithm of Typer and all
	 * @param threshold Value for the dijkstra of how much the highest betweenness must be OVER the component size -1 (community criterion) to terminate
	 */
	public static Map<String,List<Nodes>> findBetwCommunities(double threshold){
		
		//"Break the graph into connected components"
		//-> Check for compontents that are not connected
		//Start with a Node Check the shortest distances to that node
		//If there are Nodes with infinite they are in another community
		//reduce the list to the ones with infinite distance
		//repeat that for the rest until there are no others
		
		//Bool determines if there are still nodes without groups
		boolean nodesLeft = true;
		//Initialise lift to that has to get splitt
		List<Nodes> toSplitt= nodeList;
		//Initialize component list that has components of connected nodes
		List<List> components = new ArrayList<List>();
		while (nodesLeft){
			//1. Get all Nodes connected to node 0
			dijkstra(toSplitt.get(0),toSplitt);
			//Put all Nodes with distance infinity in a new list
			Iterator<Nodes> it = toSplitt.iterator();
			///List with connected nodes
			List<Nodes> connected = new ArrayList<Nodes>();
			///List with Unconnected nodes
			List<Nodes> unconnected = new ArrayList<Nodes>();
			while (it.hasNext()){
				Nodes current = it.next();
				if (current.getPrevious()!=null || current.getDistance()==0){
					connected.add(current);
				} else {
					unconnected.add(current);
				}
			}
			//connected Parts form a new component
			if (!connected.isEmpty()) components.add(connected);
			//Check if there are still unconnected components
			if (unconnected.isEmpty()){
				nodesLeft = false;
			}else{
				toSplitt=unconnected;
			}
		}
		
		//"For each component, check to see if component is a community."
		Map<String,List<Nodes>> communities = new HashMap<String,List<Nodes>>(); // List of finished communities
		int communityNumber = 1;
		int n = components.size();
		for (int i = 0; i<n;i++){
			List<Nodes> currentComp = components.get(i);
			// a component of five or less vertices can not be further divided
			if (currentComp.size()<6){
				//if (debug) System.out.println("New Community found through size criterion:\n"+currentComp);
				communities.put(""+communityNumber,currentComp);
				communityNumber++;
				components.remove(currentComp);
				i=i-1; //index is lowered because of removal
				n = components.size();
			}else{ // a component with n vertices has the highest betweenness of n-1 it is a community
				List<Edges> intraCom = new ArrayList<Edges>();
				//When size is greater threshold then a random subset is created for computational ease
				intraCom = componentBetweenness(currentComp,threshold);
				float highestBetweenness = 0;
				for (Edges intraEdge :intraCom){ 
					if (intraEdge.getWeight()>highestBetweenness) highestBetweenness = intraEdge.getWeight();
				}
				//Community Check by leaf betweenness criterium
				if (highestBetweenness <= currentComp.size()-1){
					//if (debug) System.out.println("New Community of size " +currentComp.size()+ " found trough highest betweenness("+highestBetweenness+") criterion:\n"+currentComp);
					communities.put(""+communityNumber,currentComp);
					communityNumber++;
					components.remove(currentComp);
					i=i-1; //index ist lowered because of removal
					n = components.size();
				} else { //Component is no community. Remove edges until graph is split in two components
					//find edges with highest betweenness
					List<Edges> hb = new ArrayList<Edges>();
					for (Edges ce : intraCom){
						if (ce.getWeight()==highestBetweenness) hb.add(ce);
					}
					//randomly pick one of the hb edges to delete
					int rand = Math.round((float) (Math.random()*(hb.size()-1)));
					Edges toDelete = hb.get(rand);
					
					//remove edge
					intraCom.remove(toDelete);
					removeEdge(toDelete);
					
					//Check if the edge removal has created two components
					dijkstra(currentComp.get(0),currentComp);
					List<Nodes> connected = new ArrayList<Nodes>();
					List<Nodes> unconnected = new ArrayList<Nodes>();
					for (Nodes node:currentComp){
						if (node.getPrevious()!=null || node.getDistance()==0){
							connected.add(node);
						} else {
							unconnected.add(node);
						}
					}
					//Wenn es keine unconnected gibt m�ssen weitere edges entfernt werden
					if (unconnected.size()==0){
						i=i-1; //Alle schritte f�e gleiche Komponente noch ein mal durch gehen.
					} else { // die beiden aufgespalteten Componenten weiter untersuchen
						//if (debug) System.out.println("Splitting components into: \n"+connected+"\n"+unconnected);
						components.remove(currentComp); 
						i=i-1; //index ist lowered because of removal
						components.add(connected);
						components.add(unconnected);
						n = components.size();
					}	
				}
			}
		}
		if (debug){ 	
			System.out.println("------The final communities are------\n");
			int friendSize = 0;
			for (List<Nodes> com : communities.values()){
				System.out.println("Community of size "+com.size()+":");
				System.out.println(com);
				friendSize+=com.size();
			}
			System.out.println("------You have "+friendSize+"friends");
		}
		return communities;
	}
	/**
	 * Goes through all edges and sets the neighbors for the nodes
	 */
	public static void assignNeighbors(){
		Iterator<Edges> it = edgeList.iterator();
		//go through all edges
		while (it.hasNext()){
			Edges edge = it.next();
			Nodes source = null;
			Nodes target = null;
			Iterator<Nodes> jt = nodeList.iterator();
			//For each edge go through all nodes
			while (jt.hasNext()){
				Nodes node = jt.next();
				if (edge.getSource().equals(node.getId())){
					source=node;
				} else if (edge.getTarget().equals(node.getId())){
					target=node;
				}
			}
			//set source and target as neighbors
			if (source != null && target !=null){
				source.addNeighbor(target);
				target.addNeighbor(source);
			}
		}
	}
	
	/**
	 * Calculates for all other notes the shortest distance to the source node and sets their distance to it. Then sets
	 * the predecessing node to the current node which leads to the shortest path. Also adds weights to the notes
	 * according to how often they are part of a shortest path
	 * @param source Node to which all smallest distances should be calculated.
	 * @param list List of the nodes for which the dijkstra has to be done
	 * @param calcBetweenness if this is true the edge weights are added 0.5 for each traverse
	 */
	public static List<Edges> dijkstra(Nodes source, List<Nodes> list,boolean calcBetweenness){
		if (!list.contains(source)){
			System.out.println("dijstra: source must be contained in List");
			return null;
		}
		//Reset Nodes
		Iterator<Nodes> it = list.iterator();
		while (it.hasNext()){
			Nodes n = it.next();
			n.setDistance(Double.POSITIVE_INFINITY);
			n.setPrevious(null);
		}
		//Distance for source is zero-> is the first one chosen
        source.setDistance(0.);
        //List of unvisited nodes
        PriorityQueue<Nodes> unvisited = new PriorityQueue<Nodes>();
        Nodes current = source;
        unvisited.add(source);
        while (!unvisited.isEmpty()){
        	//Get Node with the smallest distance
        	current = unvisited.poll();
        	
	        //Calculate new distances
        	if (list.contains(current))
		        for (Nodes neighbor : current.getNeighbors()){
	        		if (current.getDistance() +1 < neighbor.getDistance()){
	        			//Set new distance
	        			neighbor.setDistance(current.getDistance()+1);
	        			//add neighbor to unvisited
	        			if (!unvisited.contains(neighbor)){
	        				
	        				unvisited.add(neighbor); 
	        				
	        			}
	        			
	        			neighbor.setPrevious(current);
	        		}	        	
		        }	        
        }
        
        ////////////////////////////////////////////
        //Calc Betweenness to the source////////////
        ////////////////////////////////////////////
        List<Edges> result = new ArrayList<Edges>();
		for (Nodes akt : list){
			if (akt.getPrevious()==null){
				continue;
			}
			for (Nodes i = akt;i.getPrevious()!=null;i=i.getPrevious()){
				if (calcBetweenness)
					result.add(addWeight(i.getId(),i.getPrevious().getId(),false));
				else
					result.add(getEdge(i.getId(),i.getPrevious().getId(),false));
			}
			
		}
		return result;
	}
	
	/**
	 * Calculates for all other notes the shortest distance to the source node and sets their distance to it. Then sets
	 * the predecessing node to the current node which leads to the shortest path.
	 * @param source Node to which all smallest distances should be calculated.
	 * @param list List of the nodes for which the dijkstra has to be done 
	 */
	public static List<Edges> dijkstra(Nodes source, List<Nodes> list){
		return dijkstra (source, list, false);
	}
	
	
	/**
	 * Calculate betweenness for whole component
	 * @param component List of Nodes
	 * @return List of Edges in the component
	 */
	public static List<Edges> componentBetweenness(List<Nodes> component){
		return componentBetweenness(component, Double.POSITIVE_INFINITY);
	}
	/**
	 * Calculate betweenness for whole component
	 * @param component List of Nodes
	 * @param threshold Value of how much the highest betweenness must be OVER the component size -1 (community criterion) to terminate
	 * @return List of Edges in the component
	 */
	public static List<Edges> componentBetweenness(List<Nodes> component, double threshold){
		List<Edges> result = new ArrayList<Edges>();
		//reset weights
		for (Edges current : edgeList){
			current.setWeight(0);
		}
		//Dijkstra for all Nodes calcs betweenness
		if (threshold == Double.POSITIVE_INFINITY){
			for (Nodes current : component){
				result = dijkstra(current,component,true);
			}
		}else { //Dijkstra for as long as threshold is not overdone
			float highestBetweenness = 0;
			List<Nodes> subset = new ArrayList<Nodes>();
			while (highestBetweenness < threshold+component.size()-1 && subset.size()<component.size()){
				boolean isDrawn = false;
				Random rng = new Random();
					while (!isDrawn){
					Nodes drawn = component.get(rng.nextInt(component.size()));
					if (!subset.contains(drawn)){ 
						subset.add(drawn);
						isDrawn=true;
					}
				}
				result = dijkstra(subset.get(0),subset,true);
				for (Edges intraEdge :result){ 
					if (intraEdge.getWeight()>highestBetweenness) highestBetweenness = intraEdge.getWeight();
				}
			}
		}
		return result;
	}

	
	/**
	 * Adds a weight to an edge
	 * @param source ID of the source Node
	 * @param target ID of the target Node
	 * @param direction if not direction edge will also be weigthed if it goes into the other direction
	 * @return
	 */
	public static Edges addWeight(String source,String target, boolean directional){
		Edges edge = getEdge(source, target, directional);
		edge.setWeight(edge.getWeight()+0.5f); //0.5 because in the end it would be devided by two
		return edge;
	}
	
	/**
	 * Returns edge from source to target
	 * @param source ID of the source Node
	 * @param target ID of the target Node
	 * @param direction if not direction edge will also be weigthed if it goes into the other direction
	 * @return
	 */
	public static Edges getEdge(String source, String target, boolean directional){
		Iterator<Edges> it = edgeList.iterator();
		while (it.hasNext()){
			Edges edge = it.next();
			if ( (edge.getSource().equals(source) && edge.getTarget().equals(target))
					||(!directional && edge.getSource().equals(target) && edge.getTarget().equals(source))){				
				return edge;
			}
		}
		return null;
	}
	
	/**
	 * Removes both neighbor pointers from the source and target of the edge
	 * @param edge Edge 
	 */
	public static void removeEdge(Edges edge){
		Nodes source = null;
		Nodes target = null;
		for (Nodes node : nodeList){
			if(node.getId().equals(edge.getSource())) source = node;
			else if (node.getId().equals(edge.getTarget())) target = node;
		}
		if (source != null)	source.getNeighbors().remove(target);
		if (target != null) target.getNeighbors().remove(source);
		
	}
}
