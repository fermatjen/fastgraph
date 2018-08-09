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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private Map<Integer, Integer> hotSpots = new LinkedHashMap<>();

    /**
     * Initialize the graph with 2 files vertices and edges
     *
     * @param verticesFile A file containing comma-separated list of vertices ID
     * and vertices name
     * @param edgesFile A file containing comma-separated list of two vertices
     * ID. For example, one row could be 25432, 1276287
     */
    public FastGraph(File verticesFile, File edgesFile) {
        this(verticesFile, edgesFile, false);
    }

    /**
     * Initialize the graph with 2 files vertices and edges
     *
     * @param verticesFile A file containing comma-separated list of vertices ID
     * and vertices name
     * @param edgesFile A file containing comma-separated list of two vertices
     * ID. For example, one row could be 25432, 1276287
     * @param computeHotspots If True, when the FastGraph initializes, it
     * creates a map of hot spot vertices that will be used as preferred
     * traversal paths. The hot spots are calculated based on the triangles
     * owned by the vertices.
     */
    public FastGraph(File verticesFile, File edgesFile, boolean computeHotspots) {

        FileInputStream fis;
        try {
            //Load vertices
            fis = new FileInputStream(verticesFile);
            Scanner scanner = new Scanner(fis);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                String token = ",";
                if (!line.contains(token)) {
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
                if (!line.contains(token)) {
                    token = " ";
                }
                StringTokenizer stok = new StringTokenizer(line, token);
                int VID1 = Integer.parseInt(stok.nextToken().trim());
                int VID2 = Integer.parseInt(stok.nextToken().trim());
                //System.out.println(VID1+":"+VID2);

                LinkedHashMap edgesMap = new LinkedHashMap();
                edgesMap.put("S", VID1);
                edgesMap.put("D", VID2);

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
                    int eweight = (int) existingEdgesMap.get("W");
                    eweight++;
                    //System.out.println("Updating weight of edge "+TEID+" ("+VID1+":"+VID2+") by "+eweight);
                    existingEdgesMap.remove("W");
                    existingEdgesMap.put("W", eweight);
                    edges.remove(TEID);
                    edges.put(TEID, existingEdgesMap);
                } else {
                    //First edge
                    edgesMap.put("W", 1);
                    edges.put(TEID, edgesMap);
                }

                EID++;

                //Add in edgesHelper
                if (edgesHelper.containsKey(VID1)) {
                    //Vertex already there
                    ArrayList edgesList = edgesHelper.get(VID1);
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
                    ArrayList edgesList = edgesHelper.get(VID2);
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

            Logger.getLogger(FastGraph.class.getName()).log(Level.INFO, "FastGraph: Populated with {0} vertices and {1} edges", new Object[]{vertices.size(), edges.size()});

            if (computeHotspots) {
                Logger.getLogger(FastGraph.class.getName()).log(Level.INFO, "FastGraph: Computing hot spots...");

                hotSpots = getRankByTrianglesCount(edgesHelper.size());

                Logger.getLogger(FastGraph.class.getName()).log(Level.INFO, "FastGraph: Computing hot spots for {0} vertices...DONE", hotSpots.size());
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * A static utility function to convert any text file to FastGraph files
     *
     * @param inputTextFile File containing the lines of text to tokenize
     * @param outputVerticesFile A file containing comma-separated list of
     * vertices ID and vertices name. This will be generated.
     * @param outputEdgesFile A file containing comma-separated list of two
     * vertices ID. For example, one row could be 25432, 1276287. This will be
     * generated.
     */
    public static void createFastGraphFiles(File inputTextFile, File outputVerticesFile, File outputEdgesFile) {

        LinkedHashMap<String, Integer> verticesMap = new LinkedHashMap();
        LinkedHashMap<Integer, String> reverse_verticesMap = new LinkedHashMap();
        LinkedHashMap<Integer, ArrayList> edgesMap = new LinkedHashMap();

        try {

            //First pass
            FileInputStream fis = new FileInputStream(inputTextFile);
            Scanner scanner = new Scanner(fis);
            int VID = 1;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                //System.out.println(line);
                StringTokenizer stok = new StringTokenizer(line, " ");
                while (stok.hasMoreTokens()) {
                    String token = stok.nextToken().trim();
                    if (!verticesMap.containsKey(token)) {
                        if (!reverse_verticesMap.containsKey(VID)) {
                            verticesMap.put(token, VID);
                            reverse_verticesMap.put(VID, token);
                            VID++;
                        }

                    }
                }
            }

            fis.close();

            //System.out.println("Populated vertices: " + verticesMap.size());

            //Second pass
            fis = new FileInputStream(inputTextFile);
            scanner = new Scanner(fis);

            int EID = 1;

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                StringTokenizer stok = new StringTokenizer(line, " ");
                String prevToken = "";
                int tokenCount = 0;
                while (stok.hasMoreTokens()) {
                    String token = stok.nextToken().trim();
                    if (tokenCount == 0) {
                        //Root
                        prevToken = token;
                        tokenCount++;
                        continue;
                    }

                    int VID1 = verticesMap.get(prevToken);
                    int VID2 = verticesMap.get(token);

                    ArrayList connectionsList = new ArrayList();
                    connectionsList.add(VID1);
                    connectionsList.add(VID2);

                    edgesMap.put(EID, connectionsList);
                    EID++;
                    tokenCount++;
                }

            }
            fis.close();
            //System.out.println("Populated edges: " + edgesMap.size());

            //System.out.println("Write FastGraph files...");
            FileWriter verticesWriter = new FileWriter(outputVerticesFile);

            verticesMap.keySet().forEach((vertexName) -> {
                try {
                    int vertexID = verticesMap.get(vertexName);
                    verticesWriter.write(vertexID + ", " + vertexName + "\r\n");
                } catch (IOException ex) {
                    Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

            verticesWriter.close();

            FileWriter edgesWriter = new FileWriter(outputEdgesFile);

            edgesMap.keySet().forEach((edgeID) -> {
                try {
                    ArrayList connectionsList = edgesMap.get(edgeID);
                    //Size is always 2
                    edgesWriter.write(connectionsList.get(0) + ", " + connectionsList.get(1) + "\r\n");
                } catch (IOException ex) {
                    Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
            });

            edgesWriter.close();

        } catch (FileNotFoundException ex) {
            Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FastGraph.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     * Get all dangling vertices without any edges. These vertices are not
     * connect with any other vertices
     *
     * @return a Set containing the vertices IDs
     */
    public Set getAllVerticesWithNoEdges() {
        //Compare vertices with edges to all the vertices
        Set<Integer> allVertices = new HashSet<>(vertices.keySet());
        Set<Integer> edgedVertices = new HashSet<>(edgesHelper.keySet());
        allVertices.removeAll(edgedVertices);

        return allVertices;
    }

    /**
     * Get the ID of the vertex by its name
     *
     * @param line The name of the vertex
     * @param patternMatch Should vertex name be pattern-matched if the exact
     * vertex name is not available?
     * @return Returns vertex ID
     */
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

    /**
     * Get the vertex name from its ID
     *
     * @param VID The vertex ID
     * @return The vertex name
     */
    public String getVertexByID(int VID) {
        if (!vertices.containsKey(VID)) {
            return null;
        } else {
            return vertices.get(VID);
        }
    }

    /**
     * Get all the connections for the edge denoted by this edge ID
     *
     * @param EID The Edge ID
     * @return A LinkedHashMap containing the Source VID, Destination VID and
     * the connection weight
     */
    public LinkedHashMap getVerticesForEdge(int EID) {
        return edges.get(EID);
    }

    /**
     * Get all the edges for the vertex denoted by this vertex ID
     *
     * @param VID The Vertex ID
     * @return An ArrayList containing the list of all Edge IDs
     */
    public ArrayList getAllEdgesForVertex(int VID) {
        return edgesHelper.get(VID);
    }

    /**
     * Get the vertices count for the graph
     *
     * @return The vertices count
     */
    public int getVerticesSize() {
        return vertices.size();
    }

    /**
     * Get the edges size for the graph
     *
     * @return The edges size
     */
    public int getEdgesSize() {
        return edges.size();
    }

    /**
     * Find the number of triangles for this vertex
     *
     * @param VID1 The vertex ID
     * @return The number of triangles connecting to this vertex
     */
    public int getTrianglesCountForVertex(int VID1) {
        return getTrianglesForVertex(VID1).size();
    }

    /**
     * Find all the triangles for this vertex
     *
     * @param VID1 The vertex ID
     * @return An ArrayList of ArrayLIst containing all the cyclic paths. The
     * paths are denoted by vertex IDs. Use getVertexByID method to get the
     * vertex name.
     */
    public ArrayList getTrianglesForVertex(int VID1) {
        //Get all cyclic paths, if any
        ArrayList<ArrayList> cyclicPaths = new ArrayList();
        ArrayList<String> sortedPathList = new ArrayList();

        if (VID1 == -1) {
            return cyclicPaths;
        }
        ArrayList edgesList = edgesHelper.get(VID1);

        for (int i = 0; i < edgesList.size(); i++) {
            int EID = (int) edgesList.get(i);
            //Get vertices for this edge
            LinkedHashMap edgesMap = edges.get(EID);
            int sourceID = (int) edgesMap.get("S");
            int destID = (int) edgesMap.get("D");

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
            ArrayList edgesList2 = edgesHelper.get(neighborID);

            for (int j = 0; j < edgesList2.size(); j++) {
                int EID2 = (int) edgesList2.get(j);
                //Get vertices for this edge
                LinkedHashMap edgesMap2 = edges.get(EID2);
                int sourceID2 = (int) edgesMap2.get("S");
                int destID2 = (int) edgesMap2.get("D");

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

    /**
     * Count the number of triangles for this graph
     *
     * @return The number of triangles present in this graph
     */
    public int getTrianglesCount() {
        return getAllTriangles().size();
    }

    /**
     * Get all the triangles present in this graph
     *
     * @return An ArrayList of ArrayList containing all the triangles in this
     * graph. The paths are denoted by vertex IDs. Use getVertexByID method to
     * get the vertex name.
     */
    public ArrayList getAllTriangles() {
        //Get all cyclic paths, if any
        ArrayList<ArrayList> cyclicPaths = new ArrayList();
        ArrayList<String> sortedPathList = new ArrayList();

        for (int VID1 : vertices.keySet()) {

            if (VID1 == -1) {
                return cyclicPaths;
            }
            ArrayList edgesList = edgesHelper.get(VID1);
            if (edgesList == null) {
                continue;
            }

            for (int i = 0; i < edgesList.size(); i++) {
                int EID = (int) edgesList.get(i);
                //Get vertices for this edge
                LinkedHashMap edgesMap = edges.get(EID);
                int sourceID = (int) edgesMap.get("S");
                int destID = (int) edgesMap.get("D");

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
                ArrayList edgesList2 = edgesHelper.get(neighborID);

                for (int j = 0; j < edgesList2.size(); j++) {
                    int EID2 = (int) edgesList2.get(j);
                    //Get vertices for this edge
                    LinkedHashMap edgesMap2 = edges.get(EID2);
                    int sourceID2 = (int) edgesMap2.get("S");
                    int destID2 = (int) edgesMap2.get("D");

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

    /**
     * Get the weigh of the edge between 2 vertices
     *
     * @param VID1 Vertex 1
     * @param VID2 Vertex 2
     * @return The weight or the connection strength between these 2 vertices
     */
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
            LinkedHashMap edgesMap = edges.get(EID);
            int sourceID = (int) edgesMap.get("S");
            int destID = (int) edgesMap.get("D");

            if (sourceID == VID1 && destID == VID2) {
                return (int) edgesMap.get("W");
            }
            if (sourceID == VID2 && destID == VID1) {
                return (int) edgesMap.get("W");
            }

        }

        return -1;
    }

    /**
     * Get the count of edges for the vertex denoted by its ID
     *
     * @param VID The vertex ID
     * @return The total number of edges for this vertex ID
     */
    public int getEdgesCountForVertex(int VID) {
        return getNumOfNeighbors(VID);
    }

    /**
     * Get the count of edges for the vertex denoted by its ID
     *
     * @param VID The vertex ID
     * @return The total number of edges for this vertex ID
     */
    public int getDegreeForVertex(int VID) {
        return getNumOfNeighbors(VID);
    }

    /**
     * Get the total number of neighbors for a vertex
     *
     * @param VID The vertex ID
     * @return The count of neighbors (vertices) for this vertex
     */
    public int getNumOfNeighbors(int VID) {
        //Try edgesHelper
        if (VID == -1) {
            return VID;
        } else {
            return edgesHelper.get(VID).size();
        }
        //Another way
        //return (getNeighbors(VID, 1, false)).size();
    }

    /**
     * Ranking algorithm. Get the best ranked vertices in this graph based on
     * the triangles count
     *
     * @param maxVertices The maximum number of vertices to be returned. For
     * example, 20, indicates, top 20 ranked vertices.
     * @return A sorted map containing (Vertex ID, Rank) containing the
     * top-ranked vertices and their ranks.
     */
    public Map getRankByTrianglesCount(int maxVertices) {

        //If hotspots are available and not corrupted, return the hotspots instead
        //as they already contain the traingles count.
        if (hotSpots.size() == edgesHelper.size()) {
            LinkedHashMap<Integer, Integer> resultsMap = new LinkedHashMap();

            Iterator iter = hotSpots.keySet().iterator();

            int count = 0;
            while (iter.hasNext()) {
                if (count == maxVertices) {
                    break;
                }
                int VID = (int) iter.next();
                int size = hotSpots.get(VID);
                resultsMap.put(VID, size);
                count++;
            }
            return resultsMap;
        }

        LinkedHashMap rankedMap = new LinkedHashMap();
        //Faster way
        edgesHelper.keySet().forEach((VID) -> {
            int trianglesCount = getTrianglesCountForVertex(VID);
            rankedMap.put(VID, trianglesCount);
        });

        //Update internal hotspots
        hotSpots = rankedMap;

        Map<Integer, Integer> sortedNeighborsMap = sortByComparator(rankedMap, false);

        LinkedHashMap<Integer, Integer> resultsMap = new LinkedHashMap();

        Iterator iter = sortedNeighborsMap.keySet().iterator();

        int count = 0;
        while (iter.hasNext()) {
            if (count == maxVertices) {
                break;
            }
            int VID = (int) iter.next();
            int size = sortedNeighborsMap.get(VID);
            resultsMap.put(VID, size);
            count++;
        }
        return resultsMap;

    }

    /**
     * Ranking algorithm. Get the best ranked vertices in this graph based on
     * edges count.
     *
     * @param maxVertices The maximum number of vertices to be returned. For
     * example, 20, indicates, top 20 ranked vertices.
     * @return A sorted map containing (Vertex ID, Rank) containing the
     * top-ranked vertices and their ranks.
     */
    public Map getRankByEdgesCount(int maxVertices) {
        LinkedHashMap rankedMap = new LinkedHashMap();
        //Faster way
        edgesHelper.keySet().forEach((VID) -> {
            ArrayList edgesList = edgesHelper.get(VID);
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
            int size = sortedNeighborsMap.get(VID);
            resultsMap.put(VID, size);
            count++;
        }
        return resultsMap;

    }

    /**
     * Get the best trail (path) length for this vertex
     *
     * @param VID The vertex ID
     * @param depth The maximum depth (hops) of search
     * @return The count of hops after which teh path ends
     */
    public int getBestTrailLength(int VID, int depth) {
        return getBestTrail(VID, depth, false).size();
    }

    /**
     * Get the best trail (path) in the graph
     *
     * @param depth The maximum depth (hops) of search
     * @return A LinkedHashMap (VID, ArrayList of paths) representing the best
     * trail in the graph.
     */
    public LinkedHashMap getBestTrailInGraph(int depth) {

        ArrayList path = new ArrayList();
        int bestVID = -1;

        for (int VID : vertices.keySet()) {
            ArrayList bestTrail = getBestTrail(VID, depth, false);
            System.out.println(VID + ": " + bestTrail.size());
            if (bestTrail.size() > path.size()) {
                path = bestTrail;
                bestVID = VID;
            }
        }

        LinkedHashMap<Integer, ArrayList> bestTrail = new LinkedHashMap();
        bestTrail.put(bestVID, path);
        return bestTrail;

    }

    /**
     * Get the best trail (path) for this vertex
     *
     * @param VID The Vertex ID
     * @param depth The maximum depth (hops) of search
     * @param sortByWeights Should the connections be sorted by its weight?
     * @return An ArrayList (Vertex IDs) containing the vertices of the trail.
     */
    public ArrayList getBestTrail(int VID, int depth, boolean sortByWeights) {

        ArrayList<Integer> strongPathList = new ArrayList();
        if (VID == -1) {
            return strongPathList;
        }
        ArrayList<Integer> visitedList = new ArrayList();

        ArrayList<Integer> hotSpotsList = new ArrayList();

        Iterator hotSpotIter = hotSpots.keySet().iterator();

        int count = 0;
        int maxCount = edgesHelper.size() / 10;
        while (hotSpotIter.hasNext()) {
            //Prefer one tenth of the top vertices only
            if (count == maxCount) {
                break;
            }
            int vertexID = (int) hotSpotIter.next();
            hotSpotsList.add(vertexID);
            count++;
        }

        int SVID = VID;
        for (int i = 0; i < depth; i++) {
            //One hop only
            //System.out.println("HOP: "+i+" with "+SVID);
            Map neighbors = getNeighbors(SVID, 1, sortByWeights);
            //System.out.println(neighbors.toString());

            Iterator iter = neighbors.keySet().iterator();
            while (iter.hasNext()) {
                SVID = (int) iter.next();
                //Prefer hotspot
                if (hotSpotsList.contains(SVID)) {
                    if (!visitedList.contains(SVID)) {
                        visitedList.add(SVID);
                        if (SVID != VID) {
                            //System.out.println(SVID + ":" + VID + "  " + neighbor);
                            strongPathList.add(SVID);
                        }

                    }
                    break;
                }
                //Else the last connection is used for traversal
            }

        }

        return strongPathList;

    }

    /**
     * Find if 2 vertices are directly connected
     *
     * @param VID1 Vertex 1
     * @param VID2 Vertex 2
     * @return True if the 2 vertices are directly connected
     */
    public boolean isDirectlyConnected(int VID1, int VID2) {
        ArrayList edgeList1 = edgesHelper.get(VID1);
        //Create a scratch list because retainAll removes from
        //the original list
        ArrayList dup_edgeList1 = new ArrayList();
        dup_edgeList1.addAll(edgeList1);
        
        ArrayList edgeList2 = edgesHelper.get(VID2);
        ArrayList dup_edgeList2 = new ArrayList();
        dup_edgeList2.addAll(edgeList2);
        
        dup_edgeList1.retainAll(dup_edgeList2);

        return !dup_edgeList1.isEmpty(); //Not directly connected
    }

    /**
     * Find the best path between 2 vertices. A long-running operation for
     * massive graphs. Only 20 hops are made.
     *
     * @param VID1 Vertex I
     * @param VID2 Vertex 2
     * @param depth The maximum depth (hops) of search
     * @param sortByWeights Should the connections be sorted by its weight?
     * @return An ArrayList of vertex IDs denoting the path between the 2 given
     * vertices
     */
    public ArrayList getPathBetweenVertices(int VID1, int VID2, int depth, boolean sortByWeights) {
        ArrayList paths = new ArrayList();
        if (VID1 == -1 || VID2 == -1) {
            return paths;
        }

        //Find if they are directly connected
        if (isDirectlyConnected(VID1, VID2)) {
            paths.add(VID1);
            paths.add(VID2);

            return paths;
        }
        //We'll use nested loops instead of recursion to avoid heap issues
        //Also we search in fixed depth only
        ArrayList visitedParents = new ArrayList();

        Map cneighbors = getNeighbors(VID1, depth, sortByWeights);
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
            Map cneighbors2 = getNeighbors(neighborID, depth, sortByWeights);
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
                Map cneighbors3 = getNeighbors(neighborID2, depth, sortByWeights);
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
                    Map cneighbors4 = getNeighbors(neighborID3, depth, sortByWeights);
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
                        Map cneighbors5 = getNeighbors(neighborID4, depth, sortByWeights);
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
                            Map cneighbors6 = getNeighbors(neighborID5, depth, sortByWeights);
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
                                Map cneighbors7 = getNeighbors(neighborID6, depth, sortByWeights);
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
                                    Map cneighbors8 = getNeighbors(neighborID7, depth, sortByWeights);
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
                                        Map cneighbors9 = getNeighbors(neighborID8, depth, sortByWeights);
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
                                            Map cneighbors10 = getNeighbors(neighborID9, depth, sortByWeights);
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
                                                Map cneighbors11 = getNeighbors(neighborID10, depth, sortByWeights);
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
                                                    Map cneighbors12 = getNeighbors(neighborID11, depth, sortByWeights);
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
                                                        Map cneighbors13 = getNeighbors(neighborID12, depth, sortByWeights);
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
                                                            Map cneighbors14 = getNeighbors(neighborID13, depth, sortByWeights);
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
                                                                Map cneighbors15 = getNeighbors(neighborID14, depth, sortByWeights);
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
                                                                    Map cneighbors16 = getNeighbors(neighborID15, depth, sortByWeights);
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
                                                                        Map cneighbors17 = getNeighbors(neighborID16, depth, sortByWeights);
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
                                                                            Map cneighbors18 = getNeighbors(neighborID17, depth, sortByWeights);
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
                                                                                Map cneighbors19 = getNeighbors(neighborID18, depth, sortByWeights);
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
                                                                                    Map cneighbors20 = getNeighbors(neighborID19, depth, sortByWeights);
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

    /**
     * Find out if a certain path is a valid in the graph
     *
     * @param paths An ArrayList containing the list of all Vertex IDs
     * @return True if the path is available in the graph
     */
    public boolean isAValidPath(ArrayList paths) {
        //It is enough to proove if 2 paths are not directly connected

        int prevVID = -1;

        for (Object path : paths) {
            int VID = (int) path;
            if (prevVID != -1) {
                if (!isDirectlyConnected(VID, prevVID)) {
                    return false;
                }
            }
            prevVID = VID;
        }

        return true;
    }

    /**
     * Find all neighbors (immediate inbound and outbound vertices) for a Vertex
     *
     * @param VID The Vertex ID
     * @param depth Maximum hops
     * @param sortByWeights Should the path be taken based on edge weights
     * @return A map (Vertex ID, Weight of edges) containing the neighbors and
     * their connection strengths
     */
    public Map getNeighbors(int VID, int depth, boolean sortByWeights) {
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
            LinkedHashMap connections = edges.get(EID);
            int sourceID = (int) connections.get("S");
            int destID = (int) connections.get("D");
            int weight = (int) connections.get("W");
            //System.out.println(VID+" -> "+sourceID+":"+destID);
            if (sourceID == VID) {
                //Hit
                //neighbors.put(getVertexByID(sourceID), weight);
                neighbors.put(destID, weight);
                Map distantNeighbors = getNeighbors(destID, depth - 1, sortByWeights);
                neighbors.putAll(distantNeighbors);
            }
            if (destID == VID) {
                //Hit
                neighbors.put(sourceID, weight);
                //neighbors.put(getVertexByID(destID), weight);
                Map distantNeighbors = getNeighbors(sourceID, depth - 1, sortByWeights);
                neighbors.putAll(distantNeighbors);
            }

        });

        //Sort by value and return
        if (sortByWeights) {
            Map<Integer, Integer> sortedNeighborsMap = sortByComparator(neighbors, false);
            return sortedNeighborsMap;
        } else {
            //Try to sort by rank
            if (hotSpots.size() == edgesHelper.size()) {
                //Hot spot available. Sort by rank
                LinkedHashMap<Integer, Integer> rankedNeighborsList = new LinkedHashMap();

                neighbors.keySet().forEach((vertexID) -> {
                    int triangles = hotSpots.get(vertexID);
                    rankedNeighborsList.put(vertexID, triangles);
                });

                //Sort by triangle ownerships
                Map<Integer, Integer> sortedNeighborsMap = sortByComparator(rankedNeighborsList, false);

                return sortedNeighborsMap;
            } else {
                return neighbors;
            }
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
