package net.floodlightcontroller.configmanager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.swing.text.html.HTMLDocument.Iterator;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.hub.Hub;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.ITopologyListener;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import java.util.Timer;
import java.util.TimerTask;


public class ConfigManager implements IOFSwitchListener, IFloodlightModule, ITopologyListener, ILinkDiscoveryListener, IOFMessageListener  {
	

	protected IFloodlightProviderService floodlightProvider;
	protected ILinkDiscoveryService linkDiscoveryProvider;
	private LinkedHashMap<Long, LinkedHashMap<Long, Short>> sw_links;
	private Boolean qflag;
	private String topology_file;
	private String policy_file;
	private LinkedHashMap<String, List<String>> reachability_graph;
	private List<String> all_switches;
	private LinkedHashMap<String, Long> sw_translation;
	private List<String> all_hosts;
	private LinkedHashMap<String, List<String>> shortest_path;
	private LinkedHashMap<String, String> host_ip_to_mac;
	private LinkedHashMap<String, Short> port_mapping;
	private LinkedHashMap<String, List<LinkedHashMap<String, List<String>>>> policy_mapping;
	private LinkedHashMap<String, Boolean> if_modified;
//	protected IOFSwitchService switchServiceProvider;
	private LinkedHashMap<String, List<OFAction>> br_action_history;
	private LinkedHashMap<String, List<OFAction>> rch_action_history;
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		// return null;
		
		Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(ILinkDiscoveryService.class);
//        l.add(IOFSwitchService.class);
        return l;
	}

	public void read_file_build_policy() {
		// Read the policy files and build the policy map
		DrawMap policy_map = new DrawMap(policy_file, topology_file);
		policy_mapping =  policy_map.get_policies();
	}
	
	public void read_file_build_graph() {
		// Read the topology and policy files and build the graph
		DrawMap topology_map = new DrawMap(policy_file, topology_file);
		LinkedHashMap<String, List<String>> host_links =  topology_map.get_host_links();
		Set set = host_links.entrySet();
		java.util.Iterator i = set.iterator();
	    while(i.hasNext()) {
	    	// adding hosts connections
			Map.Entry me = (Map.Entry)i.next();
			String host_ip = (String)me.getKey();
			all_hosts.add(host_ip);
			List<String> host_conn = (List<String>)me.getValue();
			host_ip_to_mac.put(host_ip,host_conn.get(0));
			List<String> temp = new ArrayList<String>();
			temp.add(host_conn.get(1)); // switch mac
			reachability_graph.put(host_ip, temp);
			Short temp_port = Short.parseShort(host_conn.get(2));
			port_mapping.put(host_ip + "-" + host_conn.get(1),temp_port);
			port_mapping.put(host_conn.get(1) + "-" + host_ip,temp_port);
			
			// adding switch connections
			String sw_mac = (String)host_conn.get(1);
			temp = reachability_graph.get(sw_mac);
			if (temp != null) {
				temp.add(host_ip);
			}
			else {
				temp = new ArrayList<String>();
				temp.add(host_ip);
			}
			reachability_graph.put(sw_mac,temp);
	    }  
//	    System.out.println("**********$$$$$$$$$$$$$$$$$$$$");
//	    System.out.println(reachability_graph);
	}
	
	public void update_files() {
		read_file_build_graph();
		read_file_build_policy();
	}
	
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		linkDiscoveryProvider = context.getServiceImpl(ILinkDiscoveryService.class);
		// Keeps track of all links and ports for switches
		// gets updated when link added or deleted
		sw_links = new LinkedHashMap<Long, LinkedHashMap<Long, Short>>();
		qflag = true;
		reachability_graph = new LinkedHashMap<String, List<String>>();
		all_switches = new ArrayList<String>();
		all_hosts = new ArrayList<String>();
		shortest_path = new LinkedHashMap<String, List<String>>();
		host_ip_to_mac = new LinkedHashMap<String,String>();
		port_mapping = new LinkedHashMap<String,Short>();
		policy_mapping = new LinkedHashMap<String, List<LinkedHashMap<String, List<String>>>>();
		if_modified = new LinkedHashMap<String, Boolean>();
		sw_translation = new LinkedHashMap<String, Long>();
		br_action_history = new LinkedHashMap<String, List<OFAction>>();
		rch_action_history = new LinkedHashMap<String, List<OFAction>>();
		
		// parameters from properties file
		Map< String, String > configParams = context.getConfigParams(ConfigManager.class);
		String NumSwitches = configParams.get("NumSwitches");
		topology_file = configParams.get("Topology");
		policy_file = configParams.get("Policies");
		
		this.floodlightProvider.addOFSwitchListener(this);
		this.linkDiscoveryProvider.addListener(this);
		
		read_file_build_graph();
		read_file_build_policy();
		
		// read file every 5 min
		Timer timer = new Timer();
		int begin = 5*60*1000; //timer starts after x second.
		int timeinterval = 5*60*1000; //timer executes every x seconds.
		timer.scheduleAtFixedRate(new TimerTask() {
		  @Override
		  public void run() {
		    //This code is executed at every interval defined by timeinterval (eg 10 seconds) 
		   //And starts after x milliseconds defined by begin.
			  update_files();
		  }
		},begin, timeinterval);
		
//		FileReader freader = new FileReader(this);
//		Thread t1 =new Thread(freader);  
//		t1.start();
	}

	
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
//		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
//		floodlightProvider.addOFMessageListener(OFType.HELLO, this);
	}

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		// TODO Auto-generated method stub
		System.out.println("************************************");
		System.out.println("1");
		System.out.println("************************************");
		linkDiscoveryUpdate(linkUpdates);
		
	}

	@Override
	public void linkDiscoveryUpdate(LDUpdate update) {
		System.out.println("************************************");
		System.out.println("2");
		System.out.println("************************************");
		List<LDUpdate> temp = new ArrayList<LDUpdate>();
		temp.add(update);
		linkDiscoveryUpdate(temp);
	}

	public List<String> find_shortest_path(String start, String end){
		// Find shortest distance between two points
		// Uses BFS for shortest path
		
		try {
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
	//			System.out.println("1 "+node + " " +q);
				List<String> conn = reachability_graph.get(node);
				for (String i:conn){
	//				System.out.println("2 "+conn);
					if (visited.get(i) == false) {
	//					System.out.println("3 "+i);
						visited.put(i,true);
						prev.put(i,node);
						q.add(i);
						if (i.equals(end)){
	//						System.out.println("equal found");
							String sh_path = end;
							List<String> res = new ArrayList<String>();
							res.add(end);
							Boolean flag = true;
							while(flag){
								sh_path = sh_path + "-" + prev.get(i);
								res.add(prev.get(i));
								i = prev.get(i);
								if (i.equals(start)) {
									flag = false;
								}
							}
	//						return sh_path;
							List<String> path = Lists.reverse(res); 
							return path;
						}
					}			
				}
			}	
		}
		catch (NullPointerException e) {
//				System.out.println("***********FLOWMOD REVERSE**********");
//				e.printStackTrace();
			return null;
		}
//		return "not-found";
		return null;
	}
	
	public List<String> find_shortest_path_waypoint(String h1, String h2){
		// This function checks wheteher the given two endpoints have a middlebox between eachother
		// If a middle box is present, find shortest distance from h1 to middle box and then middle box to h2
		// return this distance
		
		List<LinkedHashMap<String, List<String>>> all_waypoint_rules = policy_mapping.get("waypoint");
		System.out.println("waypoint rules: "+all_waypoint_rules);
		
		// test whether h1 and h2 are in the same waypoint group
		Boolean flag = false;
		List<String> way_sw = new ArrayList<String>();
		for (LinkedHashMap<String, List<String>> i : all_waypoint_rules) {
			if (i.get("hosts").contains(h1) && i.get("hosts").contains(h2)) {
				flag = true;
				way_sw = i.get("switches");
			}
		}
		
		String p1 = h1;
		String p2 = h2;
		
		List<List<String>> all = new ArrayList<List<String>>();
		List<String> final_path = new ArrayList<String>();
		if (flag) {
			for (String i : way_sw) {
				p2 = i;
				List<String> temp = shortest_path.get(p1+"-"+p2);
				if (temp != null) {
					all.add(temp);	
				}
				p1 = i;
			}
			List<String> temp = shortest_path.get(p1+"-"+h2);
			if (temp != null) {
				all.add(temp);
			}
			
			int counter = 1;
			if (all.size() > 0) {
				for (List<String> i : all) {
					if (i.size() > 0) {
						for (String j : i) {
							if (counter == 1) {
								final_path.add(j);
							}
							else {
								counter = 1;
							}
						}
						counter = 0;
					}
				}
				return final_path;
			}
		}
		else {
			return shortest_path.get(h1+"-"+h2);
		}
		return null;
	}

	public void reverse_broadcast_rules(String h1, String h2, Short inport, Short outport, String sw) {
		// reverse rules in switches for broadcase
		// I noticed that if only src and ff:ff:ff:ff rules were installed then arp was working one-way
		// i-e when h1 ping h2, then h2's arp table was updated with correct mac. h1's arp table remained empty
		// after adding reverse rules ping returns and all tables are updated
		
    	OFFlowMod flowmod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		
				OFMatch match = new OFMatch();
				match.setDataLayerSource(host_ip_to_mac.get(h2));
				match.setDataLayerDestination(host_ip_to_mac.get(h1));
				match.setInputPort(inport);
				match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).matchOn(Flag.DL_SRC).matchOn(Flag.DL_DST));
				
				List<OFAction> actions = new ArrayList<OFAction>();
				OFAction act1 = new OFActionOutput(outport, (short)1500);
				actions.add(act1);
				
				flowmod.setMatch(match);
				flowmod.setCommand(OFFlowMod.OFPFC_ADD);
				
				flowmod.setIdleTimeout((short)0);
				flowmod.setHardTimeout((short)0);
				flowmod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
				flowmod.setActions(actions);
				flowmod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + act1.getLength()));
				
				long sw_id = sw_translation.get(sw);
				IOFSwitch ofswitch = floodlightProvider.getSwitch(sw_id);
				try {
					ofswitch.write(flowmod, null);
					ofswitch.flush();
				} catch (IOException e) {
					System.out.println("***********FLOWMOD REVERSE**********");
					e.printStackTrace();
				}
	}
	
	public void send_flow_mod_broadcast_updates() {
		// this function installs broadcast policy rules in all switches
		// gets two host in broadcase group
		// finds shortest paths between them
		// installs rules in all switches along their path
		
		List<LinkedHashMap<String, List<String>>> all_broadcast_rules = policy_mapping.get("broadcast");
		for (LinkedHashMap<String, List<String>> broadcast_rule : all_broadcast_rules) {	
			List<String> broadcast_hosts = broadcast_rule.get("hosts");
			for (String h1 : broadcast_hosts) {
				for (String h2 : broadcast_hosts) {
					if (!h1.equals(h2)) {
						Boolean ifm = if_modified.get(h1+"-"+h2);
						if (true) {
							List<String> path = shortest_path.get(h1+"-"+h2);
							if (path != null) {
								int index = 0;
								int prev_index = 0;
								for (String sw : path) {
									index = index + 1;
									if (all_switches.contains(sw)) {
								    	OFFlowMod flowmod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
										
										OFMatch match = new OFMatch();
										System.out.println("host ip: "+host_ip_to_mac.get(h1)+" "+HexString.toHexString(255));
										match.setDataLayerSource(host_ip_to_mac.get(h1));
										match.setDataLayerDestination("ff:ff:ff:ff:ff:ff");
										String prev_dst = path.get(prev_index-1);
										Short prev_port = port_mapping.get(sw +"-"+ prev_dst);
										if (prev_port != null) {
											match.setInputPort(prev_port);
											match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).matchOn(Flag.DL_SRC).matchOn(Flag.DL_DST));
											
										}
										else {
											match.setWildcards(Wildcards.FULL.matchOn(Flag.DL_SRC).matchOn(Flag.DL_DST));
											
										}
//										match.setInputPort(prev_port);
//										match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).matchOn(Flag.DL_SRC).matchOn(Flag.DL_DST));
										
										List<OFAction> actions = new ArrayList<OFAction>();
										String next_dst = path.get(index);
										Short port = port_mapping.get(sw+"-"+next_dst);
										
										OFAction act1 = new OFActionOutput(port, (short)1500);
										// check if any previous action present for the switch and same rule
										List<OFAction> temp_act = br_action_history.get(h1+"-"+"-"+"ff:ff:ff:ff:ff:ff"+"-"+prev_port);
										if (temp_act != null) {
											temp_act.add(act1);
										}
										else {
											temp_act = new ArrayList<OFAction>();
											temp_act.add(act1);
										}
										br_action_history.put(h1+"-"+"-"+"ff:ff:ff:ff:ff:ff"+"-"+prev_port, temp_act);
										
										for (OFAction k : br_action_history.get(h1+"-"+"-"+"ff:ff:ff:ff:ff:ff"+"-"+prev_port)) {
											actions.add(k);
										}
										
										flowmod.setMatch(match);
										flowmod.setCommand(OFFlowMod.OFPFC_ADD);
										
										flowmod.setIdleTimeout((short)0);
										flowmod.setHardTimeout((short)0);
										flowmod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
										
										flowmod.setActions(actions);
										int act_len = 0;
										for (OFAction k : actions) {
											act_len = act_len + k.getLength();
										}
										flowmod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + act_len));
										
										long sw_id = sw_translation.get(sw);
										
										IOFSwitch ofswitch = floodlightProvider.getSwitch(sw_id);
										try {
											ofswitch.write(flowmod, null);
											ofswitch.flush();
										} catch (IOException e) {
											System.out.println("***********FLOWMOD**********");
											e.printStackTrace();
										}
										
										reverse_broadcast_rules(h1,h2,port,prev_port,sw);
									}
									prev_index = prev_index + 1;
								}
							}
						}			
					}
				}
			}
		}
	}
	
	public void send_flow_mod_reach_updates() {
		// this function installs reach policy rules in all switches
		// gets two host in reach group
		// finds shortest paths between them
		// installs rules in all switches along their path
		// since for reach there is no restriction for middlebox, shortest path is calculated is simpler way
		
		List<LinkedHashMap<String, List<String>>> all_reach_rules = policy_mapping.get("reach");
		System.out.println("reach :"+all_reach_rules);
		for (LinkedHashMap<String, List<String>> reach_rule : all_reach_rules) {	
			List<String> reach_hosts = reach_rule.get("hosts");
			for (String h1 : reach_hosts) {
				for (String h2 : reach_hosts) {
					if (!h1.equals(h2)) {
						if (true) {
							List<String> path = shortest_path.get(h1+"-"+h2);
							int index = 0;
							int prev_index = 0;
							for (String sw : path) {
								index = index + 1;
								if (all_switches.contains(sw)) {
									
							    	OFFlowMod flowmod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
									
									OFMatch match = new OFMatch();
									match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).matchOn(Flag.DL_TYPE).matchOn(Flag.NW_DST).matchOn(Flag.NW_SRC).withNwDstMask(32).withNwSrcMask(32));
									match.setDataLayerType(Ethernet.TYPE_IPv4);
									match.setNetworkSource(IPv4.toIPv4Address(h1));
									match.setNetworkDestination(IPv4.toIPv4Address(h2));
									
									String prev_dst = path.get(prev_index-1);
									Short prev_port = port_mapping.get(sw +"-"+ prev_dst);
									match.setInputPort(prev_port);
									System.out.println("prev_port "+prev_port);
									
									List<OFAction> actions = new ArrayList<OFAction>();
									String next_dst = path.get(index);
									Short port = port_mapping.get(sw+"-"+next_dst);
									
									OFAction act1 = new OFActionOutput(port, (short)1500);
									// check if any previous action present for the switch and same rule
									List<OFAction> temp_act = rch_action_history.get(h1+"-"+"-"+h2+"-"+prev_port);
									if (temp_act != null) {
										if (!temp_act.contains(act1)){
											temp_act.add(act1);											
										}
									}
									else {
										temp_act = new ArrayList<OFAction>();
										temp_act.add(act1);
									}
									rch_action_history.put(h1+"-"+"-"+h2+"-"+prev_port, temp_act);
									
									for (OFAction k : rch_action_history.get(h1+"-"+"-"+h2+"-"+prev_port)) {
										if (!actions.contains(k)) {
											actions.add(k);
										}
									}
									actions.add(act1);
									
									flowmod.setMatch(match);
									flowmod.setCommand(OFFlowMod.OFPFC_ADD);
									
									flowmod.setIdleTimeout((short)0);
									flowmod.setHardTimeout((short)0);
									flowmod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
									
									flowmod.setActions(actions);
									int act_len = 0;
									for (OFAction k : actions) {
										act_len = act_len + k.getLength();
									}
									flowmod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + act_len));
									
									long sw_id = sw_translation.get(sw);
									
									IOFSwitch ofswitch = floodlightProvider.getSwitch(sw_id);
									try {
										ofswitch.write(flowmod, null);
										ofswitch.flush();
									} catch (IOException e) {
										System.out.println("***********FLOWMOD**********");
										e.printStackTrace();
									}
								}
								prev_index = prev_index + 1;
							}
						}			
					}
				}
			}
		}
	}
	
public void send_flow_mod_reach_waypoint_updates() {
	// this function installs reach and waypoint policy rules in all switches
	// gets two host in reach group
	// finds shortest paths between them. If a middle box is present then the middle box must be presnt along path
	// find_shortest_path_waypoint() takes care of that
	// installs rules in all switches along their path
		
		List<LinkedHashMap<String, List<String>>> all_reach_rules = policy_mapping.get("reach");
		System.out.println("reach :"+all_reach_rules);
		for (LinkedHashMap<String, List<String>> reach_rule : all_reach_rules) {	
			List<String> reach_hosts = reach_rule.get("hosts");
			for (String h1 : reach_hosts) {
				for (String h2 : reach_hosts) {
					if (!h1.equals(h2)) {
						if (true) {
							List<String> path = find_shortest_path_waypoint(h1,h2);
							if (path != null) {
								int index = 0;
								int prev_index = 0;
								for (String sw : path) {
									index = index + 1;
									if (all_switches.contains(sw)) {
										
								    	OFFlowMod flowmod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
										
										OFMatch match = new OFMatch();
										match.setWildcards(Wildcards.FULL.matchOn(Flag.IN_PORT).matchOn(Flag.DL_TYPE).matchOn(Flag.NW_DST).matchOn(Flag.NW_SRC).withNwDstMask(32).withNwSrcMask(32));
										match.setDataLayerType(Ethernet.TYPE_IPv4);
										match.setNetworkSource(IPv4.toIPv4Address(h1));
										match.setNetworkDestination(IPv4.toIPv4Address(h2));
										
										String prev_dst = path.get(prev_index-1);
										Short prev_port = port_mapping.get(sw +"-"+ prev_dst);
										match.setInputPort(prev_port);
										System.out.println("prev_port "+prev_port);
										
										List<OFAction> actions = new ArrayList<OFAction>();
										String next_dst = path.get(index);
										Short port = port_mapping.get(sw+"-"+next_dst);
										
										OFAction act1 = new OFActionOutput(port, (short)1500);
										// check if any previous action present for the switch and same rule
										List<OFAction> temp_act = rch_action_history.get(h1+"-"+"-"+h2+"-"+prev_port);
										if (temp_act != null) {
											if (!temp_act.contains(act1)){
												temp_act.add(act1);											
											}
										}
										else {
											temp_act = new ArrayList<OFAction>();
											temp_act.add(act1);
										}
										rch_action_history.put(h1+"-"+"-"+h2+"-"+prev_port, temp_act);
										
										for (OFAction k : rch_action_history.get(h1+"-"+"-"+h2+"-"+prev_port)) {
											if (!actions.contains(k)) {
												actions.add(k);
											}
										}
										actions.add(act1);
										
										flowmod.setMatch(match);
										flowmod.setCommand(OFFlowMod.OFPFC_ADD);
										
										flowmod.setIdleTimeout((short)0);
										flowmod.setHardTimeout((short)0);
										flowmod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
										
										flowmod.setActions(actions);
										int act_len = 0;
										for (OFAction k : actions) {
											act_len = act_len + k.getLength();
										}
										flowmod.setLength((short)(OFFlowMod.MINIMUM_LENGTH + act_len));
										
										long sw_id = sw_translation.get(sw);
										
										IOFSwitch ofswitch = floodlightProvider.getSwitch(sw_id);
										try {
											ofswitch.write(flowmod, null);
											ofswitch.flush();
										} catch (IOException e) {
											System.out.println("***********FLOWMOD**********");
											e.printStackTrace();
										}
									}
									prev_index = prev_index + 1;
								}
							}
						}			
					}
				}
			}
		}
	}
	
	
	public void compute_shortest_paths() {
		// compute shortest path between two points in network topology
		try {
			for (String h1:all_hosts) {
				for (String h2:all_hosts) {
					if (!h1.equals(h2)) {
						List<String> res = find_shortest_path(h1,h2);
						if (res != null) {
							shortest_path.put(h1+"-"+h2, res);
						}
					}
				}	
			}
			for (String h1:all_switches) {
				for (String h2:all_switches) {
					if (!h1.equals(h2)) {
						List<String> res = find_shortest_path(h1,h2);
						if (res != null) {
							shortest_path.put(h1+"-"+h2, res);
						}
					}
				}	
			}
			for (String h1:all_hosts) {
				for (String h2:all_switches) {
					if (!h1.equals(h2)) {
						List<String> res = find_shortest_path(h1,h2);
						if (res != null) {
							shortest_path.put(h1+"-"+h2, res);
							List<String> res_rev = Lists.reverse(res);
							shortest_path.put(h2+"-"+h1, res_rev);
						}
					}
				}	
			}
		}
		catch (ConcurrentModificationException e) {
//			System.out.println("***********FLOWMOD REVERSE**********");
//			e.printStackTrace();
	}
	}

	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
		System.out.println("************************************");
		System.out.println("3");
		System.out.println("************************************");
		
		for(LDUpdate i:updateList) {
			Long dst = i.getDst();
			Long src = i.getSrc();
			Short dstp = i.getDstPort();
			Short srcp = i.getSrcPort();
			String port_key = HexString.toHexString(src) + "-" + HexString.toHexString(dst);
			port_mapping.put(port_key, srcp);
					
//			System.out.println(HexString.toHexString(dst) + " " + HexString.toHexString(src) + " " + dstp + " " + srcp);
			LinkedHashMap<Long,Short> temp = sw_links.get(src);
			if (temp != null) {
				temp.put(dst,srcp);
			}
			else {
				temp = new LinkedHashMap<Long,Short>();
				temp.put(dst,srcp);
				sw_links.put(src,temp);
			}
			List<String> temp1 = reachability_graph.get(HexString.toHexString(src));
			if (src != (long)0 && dst != (long)0) {
				if (temp1 != null) {
				if (!temp1.contains(HexString.toHexString(dst)) && src!=dst) {
						temp1.add(HexString.toHexString(dst));
					}
				}
				else {
					temp1 = new ArrayList<String>();
					temp1.add(HexString.toHexString(dst));
				}
				reachability_graph.put(HexString.toHexString(src),temp1);
			}
		}
		
		// Update shortest paths	
//		PathFinder finder = new PathFinder(reachability_graph, all_switches, all_hosts);
//		Thread t1 =new Thread(finder);  
//		t1.start();
		
		// Calculates shortest paths for the network topology
		// Populate the shortest_path map
		compute_shortest_paths();
		
		// update flows in switches for broadcast groups		
		send_flow_mod_broadcast_updates();
		
		// update flows in switches for reach groups		
		// The function send_flow_mod_reach_waypoint_updates(); include functionality of both rean and waypoint groups
		// No need to uncomment following function
//		send_flow_mod_reach_updates();
				
		// update flows in switches for reach nad waypoint groups		
		send_flow_mod_reach_waypoint_updates();
		
		
		// For printing out all connectivity
//		System.out.println(sw_links);
//		for (String h:all_switches) {
//			System.out.println(h+" "+reachability_graph.get(h));
//		}
//		for (String h:all_hosts) {
//			System.out.println(h+" "+reachability_graph.get(h));
//		}
		
//		generate_arp_enteries();		
	}
	
//	public void generate_arp_enteries() {

//		// Read the topology and policy files and build the graph
//		DrawMap topology_map = new DrawMap(policy_file, topology_file);
//		LinkedHashMap<String, List<String>> host_links =  topology_map.get_host_links();
//		Set set = host_links.entrySet();
//		java.util.Iterator i = set.iterator();
//	    while(i.hasNext()) {
//	    	// adding hosts connections
//			Map.Entry me = (Map.Entry)i.next();
//			List<String> temp = new ArrayList<String>();
//			String key = (String)me.getKey();
//			List<String> value = (List<String>)me.getValue();
//			temp.add(value.get(1)); // switch mac
//			reachability_graph.put(key, temp);
//			
//			// adding switch connections
//			temp = new ArrayList<String>();
//			key = (String)me.getKey();
//			LinkedHashMap<Long,Short> sw_conn = sw_links.get(value.get(1));
//			Set set_temp = sw_conn.entrySet();
//			java.util.Iterator j = set_temp.iterator();
//		    while(j.hasNext()) {
//		    	Map.Entry me_temp = (Map.Entry)j.next();
//		    	temp.add((String)me_temp.getKey());
//		    }
//		    temp.add((String)me.getKey()); // host ip
//		    reachability_graph.put(key, temp);	    
//			
//	    }  
//	    System.out.println("**********$$$$$$$$$$$$$$$$$$$$");
//	    System.out.println(reachability_graph);
//	}

	@Override
	public void switchAdded(long switchId) {
		all_switches.add(HexString.toHexString(switchId));
		sw_translation.put(HexString.toHexString(switchId), switchId);
	}

	@Override
	public void switchRemoved(long switchId) {
		System.out.println("************************************");
		System.out.println("4");
		System.out.println("************************************");
		
		all_switches.remove(HexString.toHexString(switchId));
		
		// New shortest path needs to be calculated
		shortest_path = new LinkedHashMap<String, List<String>>();
		compute_shortest_paths();
	}

	@Override
	public void switchActivated(long switchId) {
	}

	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		
	}

	@Override
	public void switchChanged(long switchId) {
		
	}

	@Override
	public String getName() {
		return ConfigManager.class.getPackage().getName();
	}
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		return Command.CONTINUE;
	}
}
