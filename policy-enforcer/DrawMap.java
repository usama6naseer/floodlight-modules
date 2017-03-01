package net.floodlightcontroller.configmanager;

import java.io.*;

import java.util.*;

public class DrawMap {
	private static String policy_file;
	private static String topology_file;
	private LinkedHashMap<String, List<String>> reachability_graph;
	private List<String> all_hosts;
	private List<String> all_switches;
	
	DrawMap(String pol, String top) {
		policy_file = pol.concat(".txt");
		topology_file = top.concat(".txt");
		reachability_graph = new LinkedHashMap<>();
		all_hosts = new ArrayList<>();
		all_switches = new ArrayList<>();
	}
	
	
	public LinkedHashMap<String, List<LinkedHashMap<String, List<String>>>> get_policies() {
		LinkedHashMap<String, List<LinkedHashMap<String, List<String>>>> pol = new LinkedHashMap<String, List<LinkedHashMap<String, List<String>>>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(policy_file));
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();
		    while (line != null) {
		    	String[] str_vec = line.split("=");
		    	String pol_name = str_vec[0].replaceAll("\\s+","");
		    	String[] rhs = str_vec[1].split("\\[");
		    	if (rhs.length > 1) {
		    		// [ exists in  the policy
		    		List<String> host_arr = new ArrayList<String>();
		    		String[] hosts = rhs[0].split(",");
		    		for (String i : hosts) {
		    			i = i.replaceAll("\\s+","");
		    			i = i.replaceAll("\\{","");
		    			i = i.replaceAll("\\}","");
		    			host_arr.add(i);
		    		}
		    		List<String> sw_arr = new ArrayList<String>();
		    		String[] switches = rhs[1].split(",");
		    		for (String i : switches) {
		    			i = i.replaceAll("\\s+","");
		    			i = i.replaceAll("\\[","");
		    			i = i.replaceAll("\\]","");
		    			i = i.replaceAll("\\}","");
		    			i = i.replaceAll("\\{","");
		    			sw_arr.add(i);
		    		}
		    		LinkedHashMap<String, List<String>> temp = new LinkedHashMap<String, List<String>>();
		    		temp.put("hosts", host_arr);
		    		temp.put("switches", sw_arr);
		    		List<LinkedHashMap<String, List<String>>> tmp = pol.get(pol_name);
		    		if (tmp != null) {
		    			tmp.add(temp);
		    			pol.put(pol_name, tmp);
		    		}
		    		else {
		    			tmp = new ArrayList<LinkedHashMap<String, List<String>>>();
		    			tmp.add(temp);
		    			pol.put(pol_name, tmp);
		    		}
		    		
		    			    		
		    	}
		    	else {
		    		List<String> host_arr = new ArrayList<String>();
		    		String[] hosts = str_vec[1].split(",");
		    		for (String i : hosts) {
		    			i = i.replaceAll("\\s+","");
		    			i = i.replaceAll("\\{","");
		    			i = i.replaceAll("\\}","");
		    			host_arr.add(i);
		    		}
		    		LinkedHashMap<String, List<String>> temp = new LinkedHashMap<String, List<String>>();
		    		temp.put("hosts", host_arr);
//		    		pol.put(pol_name, temp);
		    		List<LinkedHashMap<String, List<String>>> tmp = pol.get(pol_name);
		    		if (tmp != null) {
		    			tmp.add(temp);
		    			pol.put(pol_name, tmp);
		    		}
		    		else {
		    			tmp = new ArrayList<LinkedHashMap<String, List<String>>>();
		    			tmp.add(temp);
		    			pol.put(pol_name, tmp);
		    		}
		    	}
		    	
		    	sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		} catch(FileNotFoundException e) {
			System.out.println(e);
		} catch(IOException e) {
			System.out.println(e);
		}
		return pol;		
	}
	
	public LinkedHashMap<String, List<String>> get_host_links() {
		LinkedHashMap<String, List<String>> links = new LinkedHashMap<String, List<String>>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(topology_file));
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();
		    while (line != null) {
		    	String[] str_vec = line.split(",");
		    	// Adding endhosts connectivity info
		    	String key = str_vec[0].replaceAll("\\s+","");
		    	List<String> temp = new ArrayList<String>();
		    	temp.add(str_vec[1].replaceAll("\\s+",""));
		    	temp.add(str_vec[2].replaceAll("\\s+",""));
		    	temp.add(str_vec[3].replaceAll("\\s+",""));
		    	links.put(key, temp);
		    	
		    	sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		} catch(FileNotFoundException e) {
			System.out.println(e);
		} catch(IOException e) {
			System.out.println(e);
		}
		return links;		
	}
	
	
	public void ReadFiles() {
		try {
			BufferedReader br = new BufferedReader(new FileReader(topology_file));
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
//		    	System.out.println(line);
		    	
		    	String[] str_vec = line.split(",");
		    	// Adding endhosts connectivity info
		    	String key = str_vec[0].replaceAll("\\s+","");
		    	all_hosts.add(key);
		    	List<String> temp = reachability_graph.get(key);
		    	if (temp != null) {
		    		temp.add(str_vec[2].replaceAll("\\s+",""));
		    		reachability_graph.put(key, temp);		    			
		    	}
		    	else {
		    		temp = new ArrayList<>();
		    		temp.add(str_vec[2].replaceAll("\\s+",""));
		    		reachability_graph.put(key, temp);
		    	}
		    	
		    	// Adding switches connectivity info
		    	key = str_vec[2].replaceAll("\\s+","");
		    	all_switches.add(key);
		    	temp = reachability_graph.get(key);
		    	if (temp != null) {
		    		temp.add(str_vec[0].replaceAll("\\s+",""));
		    		reachability_graph.put(key, temp);		    			
		    	}
		    	else {
		    		temp = new ArrayList<>();
		    		temp.add(str_vec[0].replaceAll("\\s+",""));
		    		reachability_graph.put(key, temp);
		    	}
		    	
		    	for (String i:all_switches){
		    		for (String j:all_switches){
		    			temp = reachability_graph.get(i);
	    				if (!i.equals(j)){
	    					if (!temp.contains(j)){
	    						temp.add(j);
	    					}
		    			}
	    				reachability_graph.put(i,temp);
			    	}
		    	}
		    	
		        sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
//		    String everything = sb.toString();
//		    System.out.println(everything);
		    br.close();
		} catch(FileNotFoundException e) {
			System.out.println(e);
		} catch(IOException e) {
			System.out.println(e);
		}
		
//		print out the reachability_graph
		Set set = reachability_graph.entrySet();
		Iterator i = set.iterator();
	    while(i.hasNext()) {
			Map.Entry me = (Map.Entry)i.next();
			System.out.print(me.getKey() + ": ");
			System.out.println(me.getValue());
	    }

	}
	
	public String find_shortest_path(String start, String end){
		Queue<String> q = new LinkedList<>();
		LinkedHashMap<String,Boolean> visited = new LinkedHashMap<>();
		LinkedHashMap<String,String> prev = new LinkedHashMap<>();
		for (String i:all_hosts){
			visited.put(i, false);
		}
		for (String i:all_switches){
			visited.put(i, false);
		}
		q.add(start);
		while (!q.isEmpty()){
			String node = q.remove();
//			System.out.println(node);
			List<String> conn = reachability_graph.get(node);
			for (String i:conn){
				if (visited.get(i) == false) {
					System.out.println(i);
					visited.put(i,true);
					prev.put(i,node);
					q.add(i);
					if (i.equals(end)){
						System.out.println("equal found");
						String sh_path = end;
						Boolean flag = true;
						while(flag){
							sh_path = sh_path + "-" + prev.get(i);
							i = prev.get(i);
							if (i.equals(start)) {
								flag = false;
							}
						}
						return sh_path;
					}
				}			
			}
		}	
		return "not-found";
	}
	
	
	
	
	public String FindShortestpath(String start, String end){
		Queue<String> q = new LinkedList<>();
		LinkedHashMap<String,Boolean> visited = new LinkedHashMap<>();
		LinkedHashMap<String,String> prev = new LinkedHashMap<>();
		for (String i:all_hosts){
			visited.put(i, false);
		}
		for (String i:all_switches){
			visited.put(i, false);
		}
		q.add(start);
		while (!q.isEmpty()){
			String node = q.remove();
//			System.out.println(node);
			List<String> conn = reachability_graph.get(node);
			for (String i:conn){
				if (visited.get(i) == false) {
					System.out.println(i);
					visited.put(i,true);
					prev.put(i,node);
					q.add(i);
					if (i.equals(end)){
						System.out.println("equal found");
						String sh_path = end;
						Boolean flag = true;
						while(flag){
							sh_path = sh_path + "-" + prev.get(i);
							i = prev.get(i);
							if (i.equals(start)) {
								flag = false;
							}
						}
						return sh_path;
					}
				}			
			}
		}	
		return "not-found";
	}

}
