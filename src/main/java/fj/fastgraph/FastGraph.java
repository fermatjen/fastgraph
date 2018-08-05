/*
 * Copyright (C) 2018 Frank Jennings
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fj.fastgraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author Frank Jennings
 */
public class FastGraph {

    private LinkedHashMap<Integer, String> vertices = new LinkedHashMap();
    private LinkedHashMap<String, Integer> reverse_vertices = new LinkedHashMap();

    private LinkedHashMap<Integer, LinkedHashMap> edges = new LinkedHashMap();
    //For fast neighbor finding
    private LinkedHashMap<Integer, ArrayList> edgesHelper = new LinkedHashMap();

    public FastGraph(File verticesFile, File edgesFile) {

        FileInputStream fis;
        try {
            //Load vertices
            fis = new FileInputStream(verticesFile);
            Scanner scanner = new Scanner(fis);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                String token = ",";
                if(!line.contains(token)){
                    token = " ";
                }
                StringTokenizer stok = new StringTokenizer(line, token);
                int VID = Integer.parseInt(stok.nextToken().trim());
                String vertex = stok.nextToken().trim();
                reverse_vertices.put(vertex, VID);
                vertices.put(VID, vertex);
            }

            fis.close();

            //Load edges
            fis = new FileInputStream(edgesFile);
            scanner = new Scanner(fis);

            LinkedHashMap visitedEdgesMap = new LinkedHashMap();

            int EID = 1;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String token = ",";
                if(!line.contains(token)){
                    token = " ";
                }
                StringTokenizer stok = new StringTokenizer(line, token);
                int VID1 = Integer.parseInt(stok.nextToken().trim());
                int VID2 = Integer.parseInt(stok.nextToken().trim());
                //System.out.println(VID1+":"+VID2);

                LinkedHashMap edgesMap = new LinkedHashMap();
                edgesMap.put("Source", VID1);
                edgesMap.put("Dest", VID2);

                //Find the correct EID to remove duplicate EIDs
                String edgeToken1 = VID1 + ":" + VID2;
                String edgeToken2 = VID2 + ":" + VID1;

                int TEID = EID;
                if (visitedEdgesMap.containsKey(edgeToken1)) {
                    TEID = (int) visitedEdgesMap.get(edgeToken1);
                } else {
                    visitedEdgesMap.put(edgeToken1, EID);
                }
                if (visitedEdgesMap.containsKey(edgeToken2)) {
                    TEID = (int) visitedEdgesMap.get(edgeToken2);
                } else {
                    visitedEdgesMap.put(edgeToken2, EID);
                }

                //Verify
                if (TEID != EID) {
                    //addweights
                    LinkedHashMap existingEdgesMap = edges.get(TEID);
                    int eweight = (int) existingEdgesMap.get("Weight");
                    eweight++;
                    //System.out.println("Updating weight of edge "+TEID+" ("+VID1+":"+VID2+") by "+eweight);
                    existingEdgesMap.remove("Weight");
                    existingEdgesMap.put("Weight", eweight);
                    edges.remove(TEID);
                    edges.put(TEID, existingEdgesMap);
                } else {
                    //First edge
                    edgesMap.put("Weight", 1);
                    edges.put(TEID, edgesMap);
                }

                EID++;

                //Add in edgesHelper
                if (edgesHelper.containsKey(VID1)) {
                    //Vertex already there
                    ArrayList edgesList = (ArrayList) edgesHelper.get(VID1);
                    edgesList.add(TEID);
                    edgesHelper.remove(VID1);
                    edgesHelper.put(VID1, edgesList);
                } else {
                    ArrayList edgesList = new ArrayList();
                    edgesList.add(TEID);
                    edgesHelper.put(VID1, edgesList);
                }
                if (edgesHelper.containsKey(VID2)) {
                    //Vertex already there
                    ArrayList edgesList = (ArrayList) edgesHelper.get(VID2);
                    if (!edgesList.contains(TEID)) {
                        edgesList.add(TEID);
                    }
                    edgesHelper.remove(VID2);
                    edgesHelper.put(VID2, edgesList);
                } else {
                    ArrayList edgesList = new ArrayList();
                    edgesList.add(TEID);
                    edgesHelper.put(VID2, edgesList);
                }

            }

            fis.close();

            System.out.println("Graph populated with " + vertices.size() + " vertices and " + edges.size() + " edges! and edges strength of " + edgesHelper.size());
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FJGraph.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FJGraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Integer getVertexByName(String line, boolean patternMatch) {

        if (!reverse_vertices.containsKey(line)) {
            //Check pattern match
            if (patternMatch) {

                Set<String> reverseSet = reverse_vertices.keySet()
                        .stream()
                        .filter(s -> s.contains(line))
                        .collect(Collectors.toSet());
                return reverse_vertices.get(reverseSet.iterator().next());
            } else {
                return -1;
            }
        } else {
            return reverse_vertices.get(line);
        }

    }

    public String getVertexByID(int VID) {
        if (!vertices.containsKey(VID)) {
            return null;
        } else {
            return vertices.get(VID);
        }
    }

    public LinkedHashMap getEdgesMap(int EID) {
        return edges.get(EID);
    }

    public int getVerticesSize() {
        return vertices.size();
    }

    public int getEdgesSize() {
        return edges.size();
    }

    public int countTrianglesForVertex(int VID1) {
        return getTrianglesForVertex(VID1).size();
    }

    public ArrayList getTrianglesForVertex(int VID1) {
        //Get all cyclic paths, if any
        ArrayList<ArrayList> cyclicPaths = new ArrayList();
        ArrayList<String> sortedPathList = new ArrayList();

        if (VID1 == -1) {
            return cyclicPaths;
        }
        ArrayList edgesList = (ArrayList) edgesHelper.get(VID1);

        for (int i = 0; i < edgesList.size(); i++) {
            int EID = (int) edgesList.get(i);
            //Get vertices for this edge
            LinkedHashMap edgesMap = (LinkedHashMap) edges.get(EID);
            int sourceID = (int) edgesMap.get("Source");
            int destID = (int) edgesMap.get("Dest");

            int neighborID = -1;
            if (sourceID == VID1) {
                neighborID = destID;
            }
            if (destID == VID1) {
                neighborID = sourceID;
            }

            if (VID1 == neighborID) {
                continue;
            }
            //Find connections for neighborID
            ArrayList edgesList2 = (ArrayList) edgesHelper.get(neighborID);

            for (int j = 0; j < edgesList2.size(); j++) {
                int EID2 = (int) edgesList2.get(j);
                //Get vertices for this edge
                LinkedHashMap edgesMap2 = (LinkedHashMap) edges.get(EID2);
                int sourceID2 = (int) edgesMap2.get("Source");
                int destID2 = (int) edgesMap2.get("Dest");

                int neighborID2 = -1;
                if (sourceID2 == neighborID) {
                    neighborID2 = destID2;
                }
                if (destID2 == neighborID) {
                    neighborID2 = sourceID2;
                }
                if (VID1 == neighborID2) {
                    continue;
                }

                //Find if VID1 is connected to neighborID2
                int weight = getWeightofEdge(VID1, neighborID2);
                if (weight != -1) {
                    ArrayList<Integer> innerCyclicPaths = new ArrayList();
                    String pathString = neighborID + "" + neighborID2;
                    char[] chars = pathString.toCharArray();
                    Arrays.sort(chars);
                    String sortedPathString = new String(chars);

                    if (!sortedPathList.contains(sortedPathString)) {
                        sortedPathList.add(sortedPathString);
                        innerCyclicPaths.add(VID1);
                        innerCyclicPaths.add(neighborID);
                        innerCyclicPaths.add(neighborID2);
                        innerCyclicPaths.add(VID1);
                        cyclicPaths.add(innerCyclicPaths);
                    }
                    //System.out.println(sortedPathString);

                }

            }
        }
        return cyclicPaths;
    }

    public int countTriangles() {
        return getAllTriangles().size();
    }

    public ArrayList getAllTriangles() {
        //Get all cyclic paths, if any
        ArrayList<ArrayList> cyclicPaths = new ArrayList();
        ArrayList<String> sortedPathList = new ArrayList();

        for (int VID1 : vertices.keySet()) {

            if (VID1 == -1) {
                return cyclicPaths;
            }
            ArrayList edgesList = (ArrayList) edgesHelper.get(VID1);
            if (edgesList == null) {
                continue;
            }

            for (int i = 0; i < edgesList.size(); i++) {
                int EID = (int) edgesList.get(i);
                //Get vertices for this edge
                LinkedHashMap edgesMap = (LinkedHashMap) edges.get(EID);
                int sourceID = (int) edgesMap.get("Source");
                int destID = (int) edgesMap.get("Dest");

                int neighborID = -1;
                if (sourceID == VID1) {
                    neighborID = destID;
                }
                if (destID == VID1) {
                    neighborID = sourceID;
                }
                if (VID1 == neighborID) {
                    continue;
                }
                //Find connections for neighborID
                ArrayList edgesList2 = (ArrayList) edgesHelper.get(neighborID);

                for (int j = 0; j < edgesList2.size(); j++) {
                    int EID2 = (int) edgesList2.get(j);
                    //Get vertices for this edge
                    LinkedHashMap edgesMap2 = (LinkedHashMap) edges.get(EID2);
                    int sourceID2 = (int) edgesMap2.get("Source");
                    int destID2 = (int) edgesMap2.get("Dest");

                    int neighborID2 = -1;
                    if (sourceID2 == neighborID) {
                        neighborID2 = destID2;
                    }
                    if (destID2 == neighborID) {
                        neighborID2 = sourceID2;
                    }
                    if (VID1 == neighborID2) {
                        continue;
                    }
                    //Find if VID1 is connected to neighborID2
                    int weight = getWeightofEdge(VID1, neighborID2);
                    if (weight != -1) {
                        ArrayList<Integer> innerCyclicPaths = new ArrayList();
                        String pathString = neighborID + "" + neighborID2;
                        char[] chars = pathString.toCharArray();
                        Arrays.sort(chars);
                        String sortedPathString = new String(chars);

                        if (!sortedPathList.contains(sortedPathString)) {
                            sortedPathList.add(sortedPathString);
                            innerCyclicPaths.add(VID1);
                            innerCyclicPaths.add(neighborID);
                            innerCyclicPaths.add(neighborID2);
                            innerCyclicPaths.add(VID1);
                            cyclicPaths.add(innerCyclicPaths);
                        }
                        //System.out.println(sortedPathString);

                    }

                }
            }
        }

        return cyclicPaths;
    }

    public int getWeightofEdge(int VID1, int VID2) {
        //Get weight between edges
        if (VID1 == -1 || VID2 == -1) {
            return -1;
        }
        ArrayList<Integer> edgeCandidates = new ArrayList();
        edgeCandidates.addAll(edgesHelper.get(VID1));
        edgeCandidates.addAll(edgesHelper.get(VID2));

        for (int i = 0; i < edgeCandidates.size(); i++) {
            int EID = edgeCandidates.get(i);
            LinkedHashMap edgesMap = (LinkedHashMap) edges.get(EID);
            int sourceID = (int) edgesMap.get("Source");
            int destID = (int) edgesMap.get("Dest");

            if (sourceID == VID1 && destID == VID2) {
                return (int) edgesMap.get("Weight");
            }
            if (sourceID == VID2 && destID == VID1) {
                return (int) edgesMap.get("Weight");
            }

        }

        return -1;
    }

    public int getEdgesCountForVertex(int VID) {
        return getNumOfNeighbors(VID);
    }

    public int getDegreeForVertex(int VID){
        return getNumOfNeighbors(VID);
    }
    public int getNumOfNeighbors(int VID) {
        //Try edgesHelper
        if (VID == -1) {
            return VID;
        } else {
            return ((ArrayList) edgesHelper.get(VID)).size();
        }
        //Another way
        //return (findNeighbors(VID, 1, false)).size();
    }
    
    public Map getRankByTrianglesCount(int maxVertices) {
        LinkedHashMap rankedMap = new LinkedHashMap();
        //Faster way
        edgesHelper.keySet().forEach((VID) -> {
            int trianglesCount = countTrianglesForVertex(VID);
            rankedMap.put(VID, trianglesCount);
        });

        Map<Integer, Integer> sortedNeighborsMap = sortByComparator(rankedMap, false);

        LinkedHashMap<Integer, Integer> resultsMap = new LinkedHashMap();

        Iterator iter = sortedNeighborsMap.keySet().iterator();

        int count = 0;
        while (iter.hasNext()) {
            if (count == maxVertices) {
                break;
            }
            int VID = (int) iter.next();
            int size = (int) sortedNeighborsMap.get(VID);
            resultsMap.put(VID, size);
            count++;
        }
        return resultsMap;

    }


    public Map getRankByEdgesCount(int maxVertices) {
        LinkedHashMap rankedMap = new LinkedHashMap();
        //Faster way
        edgesHelper.keySet().forEach((VID) -> {
            ArrayList edgesList = (ArrayList) edgesHelper.get(VID);
            //System.out.println(VID + " - " + edgesList.size());
            rankedMap.put(VID, edgesList.size());
        });

        /*
        //Method 2: Rank all vertices based on neighbor strength
        vertices.keySet().forEach((VID) -> {
            int numNeighbors = getNumOfNeighbors(VID);
            //System.out.println(VID + " - " + numNeighbors);
            rankedMap.put(VID, numNeighbors);
        });
         */
        Map<Integer, Integer> sortedNeighborsMap = sortByComparator(rankedMap, false);

        LinkedHashMap<Integer, Integer> resultsMap = new LinkedHashMap();

        Iterator iter = sortedNeighborsMap.keySet().iterator();

        int count = 0;
        while (iter.hasNext()) {
            if (count == maxVertices) {
                break;
            }
            int VID = (int) iter.next();
            int size = (int) sortedNeighborsMap.get(VID);
            resultsMap.put(VID, size);
            count++;
        }
        return resultsMap;

    }

    public ArrayList getLongestTrail(int VID, int depth) {

        ArrayList<String> longPathList = new ArrayList();
        //Not implemented
        return longPathList;

    }

    public ArrayList getBestTrail(int VID, int depth, boolean sortByWeights) {

        ArrayList<String> strongPathList = new ArrayList();
        if (VID == -1) {
            return strongPathList;
        }
        ArrayList<Integer> visitedList = new ArrayList();

        int SVID = VID;
        for (int i = 0; i < depth; i++) {
            //One hop only
            //System.out.println("HOP: "+i+" with "+SVID);
            Map neighbors = findNeighbors(SVID, 1, sortByWeights);

            Iterator iter = neighbors.keySet().iterator();
            while (iter.hasNext()) {
                String neighbor = (String) iter.next();
                SVID = getVertexByName(neighbor, false);
                if (!visitedList.contains(SVID)) {
                    visitedList.add(SVID);
                    if (SVID != VID) {
                        //System.out.println(SVID + ":" + VID + "  " + neighbor);
                        strongPathList.add(neighbor);
                    }
                    break;
                }
            }

        }

        return strongPathList;

    }

    public ArrayList findPathBetweenVertices(int VID1, int VID2, int depth, boolean sortByWeights) {
        ArrayList paths = new ArrayList();
        if (VID1 == -1 || VID2 == -1) {
            return paths;
        }
        //We'll use nested loops instead of recursion to avoid heap issues
        //Also we search in fixed depth only
        ArrayList visitedParents = new ArrayList();

        Map cneighbors = findNeighbors(VID1, depth, sortByWeights);
        //Root
        visitedParents.add(VID1);
        Iterator iter = cneighbors.keySet().iterator();

        outerloop:
        while (iter.hasNext()) {
            int neighborID = (int) iter.next();
            //System.out.println(getVertexByID(neighborID) + " - " + cneighbors.get(neighborID));
            //Check Hit
            if (neighborID == VID2) {
                paths.add(neighborID);
                paths.add(VID1);
                //System.out.println("Found!");
                break outerloop;
            }
            //2nd
            if (visitedParents.contains(neighborID)) {
                continue;
            }
            Map cneighbors2 = findNeighbors(neighborID, depth, sortByWeights);
            visitedParents.add(neighborID);
            Iterator iter2 = cneighbors2.keySet().iterator();
            while (iter2.hasNext()) {
                int neighborID2 = (int) iter2.next();
                //System.out.println("-- " + getVertexByID(neighborID2) + " - " + cneighbors2.get(neighborID2));
                //Check Hit
                if (neighborID2 == VID2) {

                    paths.add(neighborID2);
                    paths.add(neighborID);
                    paths.add(VID1);
                    //System.out.println("Found!");
                    break outerloop;
                }
                //3rd
                if (visitedParents.contains(neighborID2)) {
                    continue;
                }
                Map cneighbors3 = findNeighbors(neighborID2, depth, sortByWeights);
                visitedParents.add(neighborID2);
                Iterator iter3 = cneighbors3.keySet().iterator();
                while (iter3.hasNext()) {
                    int neighborID3 = (int) iter3.next();
                    //System.out.println("--- " + getVertexByID(neighborID3) + " - " + cneighbors3.get(neighborID3));
                    //Check Hit
                    if (neighborID3 == VID2) {

                        paths.add(neighborID3);
                        paths.add(neighborID2);
                        paths.add(neighborID);
                        paths.add(VID1);
                        //System.out.println("Found!");
                        break outerloop;
                    }
                    //4th
                    if (visitedParents.contains(neighborID3)) {
                        continue;
                    }
                    Map cneighbors4 = findNeighbors(neighborID3, depth, sortByWeights);
                    visitedParents.add(neighborID3);
                    Iterator iter4 = cneighbors4.keySet().iterator();
                    while (iter4.hasNext()) {
                        int neighborID4 = (int) iter4.next();
                        //System.out.println("---- " + getVertexByID(neighborID4) + " - " + cneighbors4.get(neighborID4));
                        //Check Hit
                        if (neighborID4 == VID2) {
                            paths.add(neighborID4);
                            paths.add(neighborID3);
                            paths.add(neighborID2);
                            paths.add(neighborID);
                            paths.add(VID1);
                            //System.out.println("Found!");
                            break outerloop;
                        }
                        //5th
                        if (visitedParents.contains(neighborID4)) {
                            continue;
                        }
                        Map cneighbors5 = findNeighbors(neighborID4, depth, sortByWeights);
                        visitedParents.add(neighborID4);
                        Iterator iter5 = cneighbors5.keySet().iterator();
                        while (iter5.hasNext()) {
                            int neighborID5 = (int) iter5.next();
                            //System.out.println("----- " + graph.getVertexByID(neighborID5) + " - " + cneighbors4.get(neighborID5));
                            //Check Hit
                            if (neighborID5 == VID2) {
                                paths.add(neighborID5);
                                paths.add(neighborID4);
                                paths.add(neighborID3);
                                paths.add(neighborID2);
                                paths.add(neighborID);
                                paths.add(VID1);
                                //System.out.println("Found!");
                                break outerloop;
                            }
                            //6th
                            if (visitedParents.contains(neighborID5)) {
                                continue;
                            }
                            Map cneighbors6 = findNeighbors(neighborID5, depth, sortByWeights);
                            visitedParents.add(neighborID5);
                            Iterator iter6 = cneighbors6.keySet().iterator();
                            while (iter6.hasNext()) {
                                int neighborID6 = (int) iter6.next();
                                //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                //Check Hit
                                if (neighborID6 == VID2) {
                                    paths.add(neighborID6);
                                    paths.add(neighborID5);
                                    paths.add(neighborID4);
                                    paths.add(neighborID3);
                                    paths.add(neighborID2);
                                    paths.add(neighborID);
                                    paths.add(VID1);
                                    //System.out.println("Found!");
                                    break outerloop;
                                }
                                //7th
                                if (visitedParents.contains(neighborID6)) {
                                    continue;
                                }
                                Map cneighbors7 = findNeighbors(neighborID6, depth, sortByWeights);
                                visitedParents.add(neighborID6);
                                Iterator iter7 = cneighbors7.keySet().iterator();
                                while (iter7.hasNext()) {
                                    int neighborID7 = (int) iter7.next();
                                    //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                    //Check Hit
                                    if (neighborID7 == VID2) {
                                        paths.add(neighborID7);
                                        paths.add(neighborID6);
                                        paths.add(neighborID5);
                                        paths.add(neighborID4);
                                        paths.add(neighborID3);
                                        paths.add(neighborID2);
                                        paths.add(neighborID);
                                        paths.add(VID1);
                                        //System.out.println("Found!");
                                        break outerloop;
                                    }
                                    //8th
                                    if (visitedParents.contains(neighborID7)) {
                                        continue;
                                    }
                                    Map cneighbors8 = findNeighbors(neighborID7, depth, sortByWeights);
                                    visitedParents.add(neighborID7);
                                    Iterator iter8 = cneighbors8.keySet().iterator();
                                    while (iter8.hasNext()) {
                                        int neighborID8 = (int) iter8.next();
                                        //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                        //Check Hit
                                        if (neighborID8 == VID2) {
                                            paths.add(neighborID8);
                                            paths.add(neighborID7);
                                            paths.add(neighborID6);
                                            paths.add(neighborID5);
                                            paths.add(neighborID4);
                                            paths.add(neighborID3);
                                            paths.add(neighborID2);
                                            paths.add(neighborID);
                                            paths.add(VID1);
                                            //System.out.println("Found!");
                                            break outerloop;
                                        }
                                        //9th
                                        if (visitedParents.contains(neighborID8)) {
                                            continue;
                                        }
                                        Map cneighbors9 = findNeighbors(neighborID8, depth, sortByWeights);
                                        visitedParents.add(neighborID8);
                                        Iterator iter9 = cneighbors9.keySet().iterator();
                                        while (iter9.hasNext()) {
                                            int neighborID9 = (int) iter9.next();
                                            //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                            //Check Hit
                                            if (neighborID9 == VID2) {
                                                paths.add(neighborID9);
                                                paths.add(neighborID8);
                                                paths.add(neighborID7);
                                                paths.add(neighborID6);
                                                paths.add(neighborID5);
                                                paths.add(neighborID4);
                                                paths.add(neighborID3);
                                                paths.add(neighborID2);
                                                paths.add(neighborID);
                                                paths.add(VID1);
                                                //System.out.println("Found!");
                                                break outerloop;
                                            }
                                            //10th
                                            if (visitedParents.contains(neighborID9)) {
                                                continue;
                                            }
                                            Map cneighbors10 = findNeighbors(neighborID9, depth, sortByWeights);
                                            visitedParents.add(neighborID9);
                                            Iterator iter10 = cneighbors10.keySet().iterator();
                                            while (iter10.hasNext()) {
                                                int neighborID10 = (int) iter10.next();
                                                //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                //Check Hit
                                                if (neighborID10 == VID2) {
                                                    paths.add(neighborID10);
                                                    paths.add(neighborID9);
                                                    paths.add(neighborID8);
                                                    paths.add(neighborID7);
                                                    paths.add(neighborID6);
                                                    paths.add(neighborID5);
                                                    paths.add(neighborID4);
                                                    paths.add(neighborID3);
                                                    paths.add(neighborID2);
                                                    paths.add(neighborID);
                                                    paths.add(VID1);
                                                    //System.out.println("Found!");
                                                    break outerloop;
                                                }
                                                //11th
                                                if (visitedParents.contains(neighborID10)) {
                                                    continue;
                                                }
                                                Map cneighbors11 = findNeighbors(neighborID10, depth, sortByWeights);
                                                visitedParents.add(neighborID10);
                                                Iterator iter11 = cneighbors11.keySet().iterator();
                                                while (iter11.hasNext()) {
                                                    int neighborID11 = (int) iter11.next();
                                                    //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                    //Check Hit
                                                    if (neighborID11 == VID2) {
                                                        paths.add(neighborID11);
                                                        paths.add(neighborID10);
                                                        paths.add(neighborID9);
                                                        paths.add(neighborID8);
                                                        paths.add(neighborID7);
                                                        paths.add(neighborID6);
                                                        paths.add(neighborID5);
                                                        paths.add(neighborID4);
                                                        paths.add(neighborID3);
                                                        paths.add(neighborID2);
                                                        paths.add(neighborID);
                                                        paths.add(VID1);
                                                        //System.out.println("Found!");
                                                        break outerloop;
                                                    }
                                                    //12th
                                                    if (visitedParents.contains(neighborID11)) {
                                                        continue;
                                                    }
                                                    Map cneighbors12 = findNeighbors(neighborID11, depth, sortByWeights);
                                                    visitedParents.add(neighborID11);
                                                    Iterator iter12 = cneighbors12.keySet().iterator();
                                                    while (iter12.hasNext()) {
                                                        int neighborID12 = (int) iter12.next();
                                                        //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                        //Check Hit
                                                        if (neighborID12 == VID2) {
                                                            paths.add(neighborID12);
                                                            paths.add(neighborID11);
                                                            paths.add(neighborID10);
                                                            paths.add(neighborID9);
                                                            paths.add(neighborID8);
                                                            paths.add(neighborID7);
                                                            paths.add(neighborID6);
                                                            paths.add(neighborID5);
                                                            paths.add(neighborID4);
                                                            paths.add(neighborID3);
                                                            paths.add(neighborID2);
                                                            paths.add(neighborID);
                                                            paths.add(VID1);
                                                            //System.out.println("Found!");
                                                            break outerloop;
                                                        }
                                                        //13th
                                                        if (visitedParents.contains(neighborID12)) {
                                                            continue;
                                                        }
                                                        Map cneighbors13 = findNeighbors(neighborID12, depth, sortByWeights);
                                                        visitedParents.add(neighborID12);
                                                        Iterator iter13 = cneighbors13.keySet().iterator();
                                                        while (iter13.hasNext()) {
                                                            int neighborID13 = (int) iter13.next();
                                                            //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                            //Check Hit
                                                            if (neighborID13 == VID2) {
                                                                paths.add(neighborID13);
                                                                paths.add(neighborID12);
                                                                paths.add(neighborID11);
                                                                paths.add(neighborID10);
                                                                paths.add(neighborID9);
                                                                paths.add(neighborID8);
                                                                paths.add(neighborID7);
                                                                paths.add(neighborID6);
                                                                paths.add(neighborID5);
                                                                paths.add(neighborID4);
                                                                paths.add(neighborID3);
                                                                paths.add(neighborID2);
                                                                paths.add(neighborID);
                                                                paths.add(VID1);
                                                                //System.out.println("Found!");
                                                                break outerloop;
                                                            }
                                                            //14th
                                                            if (visitedParents.contains(neighborID13)) {
                                                                continue;
                                                            }
                                                            Map cneighbors14 = findNeighbors(neighborID13, depth, sortByWeights);
                                                            visitedParents.add(neighborID13);
                                                            Iterator iter14 = cneighbors14.keySet().iterator();
                                                            while (iter14.hasNext()) {
                                                                int neighborID14 = (int) iter14.next();
                                                                //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                //Check Hit
                                                                if (neighborID14 == VID2) {
                                                                    paths.add(neighborID14);
                                                                    paths.add(neighborID13);
                                                                    paths.add(neighborID12);
                                                                    paths.add(neighborID11);
                                                                    paths.add(neighborID10);
                                                                    paths.add(neighborID9);
                                                                    paths.add(neighborID8);
                                                                    paths.add(neighborID7);
                                                                    paths.add(neighborID6);
                                                                    paths.add(neighborID5);
                                                                    paths.add(neighborID4);
                                                                    paths.add(neighborID3);
                                                                    paths.add(neighborID2);
                                                                    paths.add(neighborID);
                                                                    paths.add(VID1);
                                                                    //System.out.println("Found!");
                                                                    break outerloop;
                                                                }
                                                                //15th
                                                                if (visitedParents.contains(neighborID14)) {
                                                                    continue;
                                                                }
                                                                Map cneighbors15 = findNeighbors(neighborID14, depth, sortByWeights);
                                                                visitedParents.add(neighborID14);
                                                                Iterator iter15 = cneighbors15.keySet().iterator();
                                                                while (iter15.hasNext()) {
                                                                    int neighborID15 = (int) iter15.next();
                                                                    //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                    //Check Hit
                                                                    if (neighborID15 == VID2) {
                                                                        paths.add(neighborID15);
                                                                        paths.add(neighborID14);
                                                                        paths.add(neighborID13);
                                                                        paths.add(neighborID12);
                                                                        paths.add(neighborID11);
                                                                        paths.add(neighborID10);
                                                                        paths.add(neighborID9);
                                                                        paths.add(neighborID8);
                                                                        paths.add(neighborID7);
                                                                        paths.add(neighborID6);
                                                                        paths.add(neighborID5);
                                                                        paths.add(neighborID4);
                                                                        paths.add(neighborID3);
                                                                        paths.add(neighborID2);
                                                                        paths.add(neighborID);
                                                                        paths.add(VID1);
                                                                        //System.out.println("Found!");
                                                                        break outerloop;
                                                                    }
                                                                    //16th 2^4 and give up
                                                                    if (visitedParents.contains(neighborID15)) {
                                                                        continue;
                                                                    }
                                                                    Map cneighbors16 = findNeighbors(neighborID15, depth, sortByWeights);
                                                                    visitedParents.add(neighborID15);
                                                                    Iterator iter16 = cneighbors16.keySet().iterator();
                                                                    while (iter16.hasNext()) {
                                                                        int neighborID16 = (int) iter16.next();
                                                                        //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                        //Check Hit
                                                                        if (neighborID16 == VID2) {
                                                                            paths.add(neighborID16);
                                                                            paths.add(neighborID15);
                                                                            paths.add(neighborID14);
                                                                            paths.add(neighborID13);
                                                                            paths.add(neighborID12);
                                                                            paths.add(neighborID11);
                                                                            paths.add(neighborID10);
                                                                            paths.add(neighborID9);
                                                                            paths.add(neighborID8);
                                                                            paths.add(neighborID7);
                                                                            paths.add(neighborID6);
                                                                            paths.add(neighborID5);
                                                                            paths.add(neighborID4);
                                                                            paths.add(neighborID3);
                                                                            paths.add(neighborID2);
                                                                            paths.add(neighborID);
                                                                            paths.add(VID1);
                                                                            //System.out.println("Found!");
                                                                            break outerloop;
                                                                        }
                                                                        //17th
                                                                        if (visitedParents.contains(neighborID16)) {
                                                                            continue;
                                                                        }
                                                                        Map cneighbors17 = findNeighbors(neighborID16, depth, sortByWeights);
                                                                        visitedParents.add(neighborID16);
                                                                        Iterator iter17 = cneighbors17.keySet().iterator();
                                                                        while (iter17.hasNext()) {
                                                                            int neighborID17 = (int) iter17.next();
                                                                            //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                            //Check Hit
                                                                            if (neighborID17 == VID2) {
                                                                                paths.add(neighborID17);
                                                                                paths.add(neighborID16);
                                                                                paths.add(neighborID15);
                                                                                paths.add(neighborID14);
                                                                                paths.add(neighborID13);
                                                                                paths.add(neighborID12);
                                                                                paths.add(neighborID11);
                                                                                paths.add(neighborID10);
                                                                                paths.add(neighborID9);
                                                                                paths.add(neighborID8);
                                                                                paths.add(neighborID7);
                                                                                paths.add(neighborID6);
                                                                                paths.add(neighborID5);
                                                                                paths.add(neighborID4);
                                                                                paths.add(neighborID3);
                                                                                paths.add(neighborID2);
                                                                                paths.add(neighborID);
                                                                                paths.add(VID1);
                                                                                //System.out.println("Found!");
                                                                                break outerloop;
                                                                            }
                                                                            //18th
                                                                            if (visitedParents.contains(neighborID17)) {
                                                                                continue;
                                                                            }
                                                                            Map cneighbors18 = findNeighbors(neighborID17, depth, sortByWeights);
                                                                            visitedParents.add(neighborID17);
                                                                            Iterator iter18 = cneighbors18.keySet().iterator();
                                                                            while (iter18.hasNext()) {
                                                                                int neighborID18 = (int) iter18.next();
                                                                                //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                                //Check Hit
                                                                                if (neighborID18 == VID2) {
                                                                                    paths.add(neighborID18);
                                                                                    paths.add(neighborID17);
                                                                                    paths.add(neighborID16);
                                                                                    paths.add(neighborID15);
                                                                                    paths.add(neighborID14);
                                                                                    paths.add(neighborID13);
                                                                                    paths.add(neighborID12);
                                                                                    paths.add(neighborID11);
                                                                                    paths.add(neighborID10);
                                                                                    paths.add(neighborID9);
                                                                                    paths.add(neighborID8);
                                                                                    paths.add(neighborID7);
                                                                                    paths.add(neighborID6);
                                                                                    paths.add(neighborID5);
                                                                                    paths.add(neighborID4);
                                                                                    paths.add(neighborID3);
                                                                                    paths.add(neighborID2);
                                                                                    paths.add(neighborID);
                                                                                    paths.add(VID1);
                                                                                    //System.out.println("Found!");
                                                                                    break outerloop;
                                                                                }
                                                                                //19th
                                                                                if (visitedParents.contains(neighborID18)) {
                                                                                    continue;
                                                                                }
                                                                                Map cneighbors19 = findNeighbors(neighborID18, depth, sortByWeights);
                                                                                visitedParents.add(neighborID18);
                                                                                Iterator iter19 = cneighbors19.keySet().iterator();
                                                                                while (iter19.hasNext()) {
                                                                                    int neighborID19 = (int) iter19.next();
                                                                                    //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                                    //Check Hit
                                                                                    if (neighborID19 == VID2) {
                                                                                        paths.add(neighborID19);
                                                                                        paths.add(neighborID18);
                                                                                        paths.add(neighborID17);
                                                                                        paths.add(neighborID16);
                                                                                        paths.add(neighborID15);
                                                                                        paths.add(neighborID14);
                                                                                        paths.add(neighborID13);
                                                                                        paths.add(neighborID12);
                                                                                        paths.add(neighborID11);
                                                                                        paths.add(neighborID10);
                                                                                        paths.add(neighborID9);
                                                                                        paths.add(neighborID8);
                                                                                        paths.add(neighborID7);
                                                                                        paths.add(neighborID6);
                                                                                        paths.add(neighborID5);
                                                                                        paths.add(neighborID4);
                                                                                        paths.add(neighborID3);
                                                                                        paths.add(neighborID2);
                                                                                        paths.add(neighborID);
                                                                                        paths.add(VID1);
                                                                                        //System.out.println("Found!");
                                                                                        break outerloop;
                                                                                    }
                                                                                    //20th
                                                                                    if (visitedParents.contains(neighborID19)) {
                                                                                        continue;
                                                                                    }
                                                                                    Map cneighbors20 = findNeighbors(neighborID19, depth, sortByWeights);
                                                                                    visitedParents.add(neighborID19);
                                                                                    Iterator iter20 = cneighbors20.keySet().iterator();
                                                                                    while (iter20.hasNext()) {
                                                                                        int neighborID20 = (int) iter20.next();
                                                                                        //System.out.println("------ " + getVertexByID(neighborID6) + " - " + cneighbors6.get(neighborID6));
                                                                                        //Check Hit
                                                                                        if (neighborID20 == VID2) {
                                                                                            paths.add(neighborID20);
                                                                                            paths.add(neighborID19);
                                                                                            paths.add(neighborID18);
                                                                                            paths.add(neighborID17);
                                                                                            paths.add(neighborID16);
                                                                                            paths.add(neighborID15);
                                                                                            paths.add(neighborID14);
                                                                                            paths.add(neighborID13);
                                                                                            paths.add(neighborID12);
                                                                                            paths.add(neighborID11);
                                                                                            paths.add(neighborID10);
                                                                                            paths.add(neighborID9);
                                                                                            paths.add(neighborID8);
                                                                                            paths.add(neighborID7);
                                                                                            paths.add(neighborID6);
                                                                                            paths.add(neighborID5);
                                                                                            paths.add(neighborID4);
                                                                                            paths.add(neighborID3);
                                                                                            paths.add(neighborID2);
                                                                                            paths.add(neighborID);
                                                                                            paths.add(VID1);
                                                                                            //System.out.println("Found!");
                                                                                            break outerloop;
                                                                                        }
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return paths;

    }

    private boolean canSkipEdgeForVertex(int VID, int EID) {

        if (edgesHelper.containsKey(VID)) {
            ArrayList edgesList = (ArrayList) edgesHelper.get(VID);
            return !edgesList.contains(EID); //Known connection
            //Can skip
        } else {
            return true;
        }
    }

    public Map findNeighbors(int VID, int depth, boolean sortByWeights) {
        //System.out.println("Looking for neighbors for "+VID+" at depth "+depth);
        HashMap<Integer, Integer> neighbors = new HashMap();

        if (VID == -1) {
            return neighbors;
        }

        if (depth == 0) {
            return neighbors;
        }

        edges.keySet().forEach((EID) -> {
            //System.out.println("Skipping "+EID+" for "+VID);
            //Check if this edge can be skipped
            if (!(canSkipEdgeForVertex(VID, EID))) {
                LinkedHashMap connections = edges.get(EID);
                int sourceID = (int) connections.get("Source");
                int destID = (int) connections.get("Dest");
                int weight = (int) connections.get("Weight");
                //System.out.println(VID+" -> "+sourceID+":"+destID);
                if (sourceID == VID) {
                    //Hit
                    //neighbors.put(getVertexByID(sourceID), weight);
                    neighbors.put(destID, weight);
                    Map distantNeighbors = findNeighbors(destID, depth - 1, sortByWeights);
                    neighbors.putAll(distantNeighbors);
                }
                if (destID == VID) {
                    //Hit
                    neighbors.put(sourceID, weight);
                    //neighbors.put(getVertexByID(destID), weight);
                    Map distantNeighbors = findNeighbors(sourceID, depth - 1, sortByWeights);
                    neighbors.putAll(distantNeighbors);
                }
            }
        });

        //Sort by value and return
        if (sortByWeights) {
            Map<Integer, Integer> sortedNeighborsMap = sortByComparator(neighbors, false);
            return sortedNeighborsMap;
        } else {
            return neighbors;
        }
    }

    private Map<Integer, Integer> sortByComparator(Map<Integer, Integer> unsortMap, final boolean order) {

        List<Map.Entry<Integer, Integer>> list = new LinkedList<>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, (Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) -> {
            if (order) {
                return o1.getValue().compareTo(o2.getValue());
            } else {
                return o2.getValue().compareTo(o1.getValue());

            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<Integer, Integer> sortedMap = new LinkedHashMap<>();
        list.forEach((entry) -> {
            sortedMap.put(entry.getKey(), entry.getValue());
        });

        return sortedMap;
    }

}
