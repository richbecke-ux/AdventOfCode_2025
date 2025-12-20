package aoc2025.day9.scifimap

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.impl.CoordinateArraySequence

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// ============================================================
// 1. CONFIGURATION & PALETTES
// ============================================================
def TARGET_VERTICES = 3000
def CANVAS_SIZE = 100000
def IMAGE_SIZE = 4096
def MIN_BOUND = 25000
def MAX_BOUND = 75000
def THICKNESS = 15000
def RANDOM_SEED = System.currentTimeMillis()

// Palettes: [Background, FillStart, FillEnd, Glow, Accent]
def PALETTES = [
        0: [name: "Magma",    bg: new Color(10, 5, 5),    fill1: new Color(50, 10, 10), fill2: new Color(100, 30, 10), glow: new Color(255, 100, 0), accent: new Color(255, 200, 0)],
        1: [name: "Ocean",    bg: new Color(2, 5, 15),    fill1: new Color(5, 20, 40),  fill2: new Color(10, 50, 90),  glow: new Color(0, 200, 255),   accent: new Color(100, 255, 255)],
        2: [name: "Matrix",   bg: new Color(0, 10, 0),    fill1: new Color(0, 30, 0),   fill2: new Color(0, 60, 0),   glow: new Color(50, 255, 50),   accent: new Color(150, 255, 150)],
        3: [name: "Cyber",    bg: new Color(10, 5, 15),   fill1: new Color(30, 0, 40),  fill2: new Color(60, 0, 80),  glow: new Color(255, 0, 255),   accent: new Color(0, 255, 255)],
        4: [name: "Gold",     bg: new Color(15, 15, 15),  fill1: new Color(50, 40, 10), fill2: new Color(100, 90, 30), glow: new Color(255, 215, 0),   accent: new Color(255, 255, 200)],
        5: [name: "Ice",      bg: new Color(20, 25, 30),  fill1: new Color(40, 50, 70), fill2: new Color(70, 90, 120), glow: new Color(150, 220, 255), accent: new Color(255, 255, 255)],
        6: [name: "Void",     bg: new Color(5, 5, 5),     fill1: new Color(20, 20, 20), fill2: new Color(50, 50, 50), glow: new Color(255, 255, 255), accent: new Color(150, 150, 150)]
]

// ============================================================
// 2. ARGUMENT PARSING
// ============================================================
def rnd = new Random(RANDOM_SEED)

def printUsage = {
    println "Usage: groovy script.groovy [options]"
    println "Options:"
    println "  -q, -l, -c, -h, -t, -x, -s  : Select base shape (Default: Random)"
    println "  -p <n>                      : Select palette by number (Default: Random)"
    println "  -?                          : Show this help"
    println "\nAvailable Palettes:"
    PALETTES.each { id, p -> println "  ${id}: ${p.name}" }
    System.exit(0)
}

if (args.contains("-?") || args.contains("-help")) printUsage()

def shapes = ['q', 'l', 'c', 'h', 't', 'x', 's']
def shapeType = null
if (args.any { it == '-t' }) shapeType = 't'
if (args.any { it == '-x' }) shapeType = 'x'
if (args.any { it == '-s' }) shapeType = 's'
if (args.any { it == '-l' }) shapeType = 'l'
if (args.any { it == '-h' }) shapeType = 'h'
if (args.any { it == '-c' }) shapeType = 'c'
if (args.any { it == '-q' }) shapeType = 'q'
if (!shapeType) shapeType = shapes[rnd.nextInt(shapes.size())]

def paletteId = -1
def pIndex = args.findIndexOf { it == '-p' }
if (pIndex > -1 && pIndex + 1 < args.size() && args[pIndex+1].isNumber()) {
    paletteId = args[pIndex+1].toInteger()
}

if (!PALETTES.containsKey(paletteId)) {
    if (pIndex > -1) {
        println "Error: Invalid palette ID '${args[pIndex+1]}'"
        printUsage()
    }
    paletteId = rnd.nextInt(PALETTES.size())
}

def currentPalette = PALETTES[paletteId]
def argCount = args.find { it.isNumber() && it != paletteId.toString() && (pIndex == -1 || it != args[pIndex+1]) }
if (argCount) TARGET_VERTICES = argCount.toInteger()

println "------------------------------------------------"
println "Generating: ${shapeType.toUpperCase()}-Shape"
println "Palette   : [${paletteId}] ${currentPalette.name}"
println "Seed      : ${RANDOM_SEED}"
println "Targets   : ${TARGET_VERTICES} vertices"
println "------------------------------------------------"

// ============================================================
// 3. PHASE CONFIGURATION
// ============================================================
def phases = [
        [name: "Macro", count: 12, minLen: 8000, maxLen: 22000, minDepth: 4000, maxDepth: 10000, steps: 6, minGap: 4000, types: ["pyramid", "pyramid", "round", "round", "box"]],
        [name: "Meso-Major", count: 48, minLen: 3000, maxLen: 9000, minDepth: 2000, maxDepth: 5000, steps: 4, minGap: 2000, types: ["pyramid", "pyramid", "round", "round", "box"]],
        // CHANGED: count from 100 to 200
        [name: "Meso-Minor", count: 200, minLen: 1200, maxLen: 3500, minDepth: 600, maxDepth: 2500, steps: 2, minGap: 800, types: ["box", "box", "pyramid", "round"]],
        [name: "Micro", count: -1, minLen: 300, maxLen: 1200, minDepth: 150, maxDepth: 1000, steps: 0, minGap: 250, types: ["box", "box", "pyramid"]]
]

// ============================================================
// 4. LOGICAL EDGE SYSTEM
// ============================================================

/**
 * Represents a logical edge that may span multiple physical vertices.
 * Types:
 *   - 'orthogonal': A single horizontal or vertical segment
 *   - 'diagonal': A stair-stepped approximation of a diagonal line (pyramid profile)
 *   - 'curve': A stair-stepped approximation of a curve (round profile)
 */
class LogicalEdge {
    String type              // 'orthogonal', 'diagonal', 'curve'
    List<Long> logicalStart  // Logical start point (where feature conceptually begins)
    List<Long> logicalEnd    // Logical end point (where feature conceptually ends)
    double logicalLength     // Straight-line distance from logicalStart to logicalEnd
    int vertexStartIdx       // Index of first vertex in the polygon for this edge
    int vertexCount          // Number of vertices this edge spans (1 for orthogonal)
    int direction            // Extrusion direction used when creating this edge (+1 or -1)
    boolean isHoriz          // Whether the baseline is horizontal
    int steps                // Number of steps used (for diagonal/curve)

    /**
     * Calculate the perpendicular direction for feature extrusion.
     * Returns normalized [dx, dy] perpendicular to the logical line.
     */
    List<Double> getPerpendicular() {
        double dx = logicalEnd[0] - logicalStart[0]
        double dy = logicalEnd[1] - logicalStart[1]
        if (logicalLength < 0.001) return [0.0, 0.0]
        // Perpendicular: rotate 90 degrees
        return [-dy / logicalLength, dx / logicalLength]
    }

    /**
     * Find where a parametric position t (0.0 to 1.0) falls on the actual vertices.
     * Returns [vertexIndex, needsSplit, pointCoordinates]
     */
    List findAttachmentPoint(List<List<Long>> allVertices, double t) {
        int n = allVertices.size()
        if (n == 0) return null

        // Clamp indices to valid range
        int safeStartIdx = Math.max(0, Math.min(vertexStartIdx, n - 1))

        if (type == 'orthogonal') {
            // Single segment - interpolate directly
            def v1 = allVertices[safeStartIdx]
            def v2 = allVertices[(safeStartIdx + 1) % n]
            def pt = [
                    (long)(v1[0] + (v2[0] - v1[0]) * t),
                    (long)(v1[1] + (v2[1] - v1[1]) * t)
            ]
            boolean needsSplit = (t > 0.01 && t < 0.99)
            return [safeStartIdx, needsSplit, pt]
        }

        // For diagonal/curve: walk the constituent segments
        // Use Manhattan distance for stair-step segments
        double totalLength = 0
        def segments = []

        // Calculate how many segments we actually have
        int numSegments = Math.min(vertexCount, n - safeStartIdx)
        if (numSegments < 1) numSegments = 1

        for (int i = 0; i < numSegments; i++) {
            int idx = safeStartIdx + i
            def v1 = allVertices[idx]
            def v2 = allVertices[(idx + 1) % n]
            double len = Math.abs(v2[0] - v1[0]) + Math.abs(v2[1] - v1[1])
            segments << [idx: idx, len: len, v1: v1, v2: v2]
            totalLength += len
        }

        if (totalLength < 0.001 || segments.isEmpty()) {
            return [safeStartIdx, false, allVertices[safeStartIdx]]
        }

        // Find which segment contains parametric position t
        double targetDist = t * totalLength
        double accumulated = 0

        for (seg in segments) {
            if (accumulated + seg.len >= targetDist - 0.001) {
                // t falls within this segment
                double localT = (seg.len > 0.001) ? (targetDist - accumulated) / seg.len : 0
                localT = Math.max(0, Math.min(1, localT))
                def pt = [
                        (long)(seg.v1[0] + (seg.v2[0] - seg.v1[0]) * localT),
                        (long)(seg.v1[1] + (seg.v2[1] - seg.v1[1]) * localT)
                ]
                boolean needsSplit = (localT > 0.01 && localT < 0.99)
                return [seg.idx, needsSplit, pt]
            }
            accumulated += seg.len
        }

        // Edge case: t ≈ 1.0, return last vertex of this edge
        def lastSeg = segments[-1]
        return [lastSeg.idx, false, lastSeg.v2]
    }

    @Override
    String toString() {
        return "LogicalEdge[${type}, len=${(int)logicalLength}, verts=${vertexCount}, start=${logicalStart}]"
    }
}

/**
 * Registry that maintains the logical edge structure of the polygon.
 */
class EdgeRegistry {
    List<LogicalEdge> edges = []

    /**
     * Initialize registry from a simple polygon where each edge is orthogonal.
     */
    void initializeFromVertices(List<List<Long>> vertices) {
        edges.clear()
        int n = vertices.size()
        for (int i = 0; i < n; i++) {
            def v1 = vertices[i]
            def v2 = vertices[(i + 1) % n]
            edges << new LogicalEdge(
                    type: 'orthogonal',
                    logicalStart: [v1[0], v1[1]],
                    logicalEnd: [v2[0], v2[1]],
                    logicalLength: Math.hypot(v2[0] - v1[0], v2[1] - v1[1]),
                    vertexStartIdx: i,
                    vertexCount: 1,
                    direction: 0,
                    isHoriz: (v1[1] == v2[1]),
                    steps: 0
            )
        }
    }

    /**
     * Select an edge weighted by logical length, filtering by minimum length.
     */
    LogicalEdge selectWeightedEdge(double minLength, Random rnd) {
        def eligible = edges.findAll { it.logicalLength >= minLength }
        if (eligible.isEmpty()) return null

        double totalWeight = eligible.sum { it.logicalLength }
        double r = rnd.nextDouble() * totalWeight
        double acc = 0
        for (edge in eligible) {
            acc += edge.logicalLength
            if (r <= acc) return edge
        }
        return eligible[-1]
    }

    /**
     * Recalculate all vertex indices after the vertex list has changed.
     */
    void reindexAll() {
        int currentIdx = 0
        for (edge in edges) {
            edge.vertexStartIdx = currentIdx
            currentIdx += edge.vertexCount
        }
    }

    /**
     * Get total vertex count implied by all edges.
     */
    int getTotalVertexCount() {
        return edges.sum { it.vertexCount } ?: 0
    }

    /**
     * Find which edge contains a given vertex index.
     */
    int findEdgeContaining(int vertexIdx) {
        for (int i = 0; i < edges.size(); i++) {
            def edge = edges[i]
            if (vertexIdx >= edge.vertexStartIdx &&
                    vertexIdx < edge.vertexStartIdx + edge.vertexCount) {
                return i
            }
        }
        return -1
    }
}

// ============================================================
// 5. GEOMETRY HELPERS
// ============================================================

def gf = new GeometryFactory()

def toJTSPolygon(List<List<Long>> verts, GeometryFactory factory) {
    if (verts.isEmpty()) return null
    Coordinate[] coords = new Coordinate[verts.size() + 1]
    for (int i = 0; i < verts.size(); i++) {
        coords[i] = new Coordinate(verts[i][0] as double, verts[i][1] as double)
    }
    coords[verts.size()] = coords[0]
    LinearRing shell = factory.createLinearRing(new CoordinateArraySequence(coords))
    return factory.createPolygon(shell, null)
}

def pointsEqual = { p1, p2, long tolerance = 1 ->
    return Math.abs(p1[0] - p2[0]) <= tolerance &&
            Math.abs(p1[1] - p2[1]) <= tolerance
}

/**
 * Generate stair-step vertices for a linear (pyramid) profile.
 * Steps go from start toward end, creating orthogonal stair-steps.
 */
def generateLinearStairVertices = { start, end, int steps ->
    if (steps <= 0) {
        // No steps - just create an L-shaped path
        if (start[0] != end[0] && start[1] != end[1]) {
            return [start, [start[0], end[1]], end]
        }
        return [start, end]
    }

    def result = []
    long dx = end[0] - start[0]
    long dy = end[1] - start[1]

    // Generate intermediate target points along the diagonal
    def targets = []
    for (int i = 0; i <= steps + 1; i++) {
        double t = i / (double)(steps + 1)
        targets << [(long)(start[0] + dx * t), (long)(start[1] + dy * t)]
    }

    // Convert to stair-steps
    for (int i = 0; i < targets.size() - 1; i++) {
        def p1 = targets[i]
        def p2 = targets[i + 1]
        result << p1
        if (p1[0] != p2[0] && p1[1] != p2[1]) {
            // Add intermediate point to make orthogonal step
            result << [p1[0], p2[1]]
        }
    }
    result << targets[-1]

    return result
}

/**
 * Generate stair-step vertices for a curved (round) profile.
 * Uses sine/cosine to create a quarter-circle approximation.
 */
def generateRoundStairVertices = { start, end, int steps, boolean isHoriz ->
    if (steps <= 0) {
        if (start[0] != end[0] && start[1] != end[1]) {
            return [start, [start[0], end[1]], end]
        }
        return [start, end]
    }

    def targets = []
    long dx_total = end[0] - start[0]
    long dy_total = end[1] - start[1]

    for (int i = 0; i <= steps + 1; i++) {
        double t = i / (double)(steps + 1)
        double angle = t * (Math.PI / 2.0)
        double curveX = Math.sin(angle)
        double curveY = 1.0 - Math.cos(angle)

        long tx, ty
        if (isHoriz) {
            tx = start[0] + (long)(dx_total * curveX)
            ty = start[1] + (long)(dy_total * curveY)
        } else {
            tx = start[0] + (long)(dx_total * curveY)
            ty = start[1] + (long)(dy_total * curveX)
        }
        targets << [tx, ty]
    }

    // Convert to stair-steps
    def result = []
    for (int i = 0; i < targets.size() - 1; i++) {
        def p1 = targets[i]
        def p2 = targets[i + 1]
        result << p1
        if (p1[0] != p2[0] && p1[1] != p2[1]) {
            result << [p1[0], p2[1]]
        }
    }
    result << targets[-1]

    return result
}

/**
 * Convert a list of points into an orthogonal (stair-stepped) path.
 * Each diagonal segment becomes an L-shaped pair of segments.
 */
def makeOrthogonalPath = { List<List<Long>> points ->
    if (points.size() < 2) return points

    def result = []
    for (int i = 0; i < points.size() - 1; i++) {
        def p1 = points[i]
        def p2 = points[i + 1]
        result << p1

        // If diagonal, insert intermediate point to make orthogonal
        if (p1[0] != p2[0] && p1[1] != p2[1]) {
            // Choose to go horizontal first, then vertical
            result << [p2[0], p1[1]]
        }
    }
    result << points[-1]
    return result
}

/**
 * Splice a feature onto the polygon, replacing vertices between two attachment points.
 * Returns the new vertex list with internal vertices removed and feature vertices inserted.
 * CRITICAL: Ensures all connections are orthogonal by adding intermediate vertices where needed.
 */
def spliceFeature = { List<List<Long>> vertices, int startVertexIdx, boolean startNeedsSplit,
                      List<Long> startPoint, int endVertexIdx, boolean endNeedsSplit,
                      List<Long> endPoint, List<List<Long>> featureVerts ->

    def newVertices = []
    int n = vertices.size()

    // Sanity check: ensure startVertexIdx <= endVertexIdx
    if (startVertexIdx > endVertexIdx) {
        int tmp = startVertexIdx; startVertexIdx = endVertexIdx; endVertexIdx = tmp
        boolean tmpB = startNeedsSplit; startNeedsSplit = endNeedsSplit; endNeedsSplit = tmpB
        def tmpPt = startPoint; startPoint = endPoint; endPoint = tmpPt
        featureVerts = featureVerts.reverse()
    }

    // Part 1: All vertices BEFORE the start attachment point
    for (int i = 0; i < startVertexIdx; i++) {
        newVertices << vertices[i]
    }

    // Part 2: Handle the start attachment with orthogonal connection
    def lastBeforeFeature = vertices[startVertexIdx]
    newVertices << lastBeforeFeature

    if (startNeedsSplit && !pointsEqual(lastBeforeFeature, startPoint)) {
        // Add split point, ensuring orthogonal connection
        newVertices << startPoint
    }

    // Get the effective "entry point" to the feature
    def entryPoint = newVertices[-1]
    def firstFeatureVert = featureVerts[0]

    // If entry point and first feature vertex are diagonal, add intermediate point
    if (entryPoint[0] != firstFeatureVert[0] && entryPoint[1] != firstFeatureVert[1]) {
        // Add orthogonal connector (go horizontal first, then vertical)
        newVertices << [firstFeatureVert[0], entryPoint[1]]
    }

    // Part 3: Add feature vertices, avoiding duplicates
    for (int i = 0; i < featureVerts.size(); i++) {
        def fv = featureVerts[i]
        if (newVertices.isEmpty() || !pointsEqual(fv, newVertices[-1])) {
            newVertices << fv
        }
    }

    // Part 4: Handle the end attachment with orthogonal connection
    def lastFeatureVert = newVertices[-1]
    def exitPoint = endNeedsSplit ? endPoint : vertices[endVertexIdx]

    // If last feature vertex and exit point are diagonal, add intermediate point
    if (lastFeatureVert[0] != exitPoint[0] && lastFeatureVert[1] != exitPoint[1]) {
        // Add orthogonal connector
        newVertices << [exitPoint[0], lastFeatureVert[1]]
    }

    if (endNeedsSplit) {
        if (!pointsEqual(newVertices[-1], endPoint)) {
            newVertices << endPoint
        }
        // Resume from endVertexIdx + 1
        for (int i = endVertexIdx + 1; i < n; i++) {
            def v = vertices[i]
            def last = newVertices[-1]
            // Ensure orthogonal connection to next vertex
            if (last[0] != v[0] && last[1] != v[1]) {
                newVertices << [v[0], last[1]]
            }
            if (!pointsEqual(v, newVertices[-1])) {
                newVertices << v
            }
        }
    } else {
        // Resume from endVertexIdx onward
        for (int i = endVertexIdx; i < n; i++) {
            def v = vertices[i]
            def last = newVertices[-1]
            // Ensure orthogonal connection
            if (last[0] != v[0] && last[1] != v[1]) {
                newVertices << [v[0], last[1]]
            }
            if (!pointsEqual(v, newVertices[-1])) {
                newVertices << v
            }
        }
    }

    // Final check: ensure the closing edge (last vertex to first vertex) is orthogonal
    def lastV = newVertices[-1]
    def firstV = newVertices[0]
    if (lastV[0] != firstV[0] && lastV[1] != firstV[1]) {
        // Add connector before closing
        newVertices << [firstV[0], lastV[1]]
    }

    // Remove any accidental consecutive duplicates
    def cleaned = [newVertices[0]]
    for (int i = 1; i < newVertices.size(); i++) {
        if (!pointsEqual(newVertices[i], cleaned[-1])) {
            cleaned << newVertices[i]
        }
    }

    return cleaned
}

/**
 * Create logical edges for a newly added feature.
 * extrudeVertically: true if extrusion is in Y direction, false if in X direction
 */
def createFeatureEdges = { String featureType, List<Long> n1, List<Long> n2,
                           List<Long> n3, List<Long> n4, int steps,
                           boolean extrudeVertically, int direction, List<List<Long>> featureVerts ->

    def newEdges = []

    // For box with orthogonal path conversion, we have more vertices
    // n1 -> corner -> n2 -> n3 -> corner -> n4

    if (featureType == 'box') {
        // Box creates: side1 (n1 to n2 area), top (n2 to n3), side2 (n3 to n4 area)
        // After makeOrthogonalPath, each "diagonal" becomes 2 segments
        // Side edges are perpendicular to the base, top edge is parallel

        // Side 1: n1 to n2 (extrusion direction)
        newEdges << new LogicalEdge(
                type: 'orthogonal',
                logicalStart: [n1[0], n1[1]],
                logicalEnd: [n2[0], n2[1]],
                logicalLength: Math.hypot(n2[0] - n1[0], n2[1] - n1[1]),
                vertexStartIdx: 0,
                vertexCount: 2,  // Two segments after orthogonalization
                direction: direction,
                isHoriz: !extrudeVertically,  // If extruding vertically, side is vertical
                steps: 0
        )
        // Top: n2 to n3
        newEdges << new LogicalEdge(
                type: 'orthogonal',
                logicalStart: [n2[0], n2[1]],
                logicalEnd: [n3[0], n3[1]],
                logicalLength: Math.hypot(n3[0] - n2[0], n3[1] - n2[1]),
                vertexStartIdx: 0,
                vertexCount: 1,
                direction: direction,
                isHoriz: extrudeVertically,  // Top runs opposite to extrusion
                steps: 0
        )
        // Side 2: n3 to n4
        newEdges << new LogicalEdge(
                type: 'orthogonal',
                logicalStart: [n3[0], n3[1]],
                logicalEnd: [n4[0], n4[1]],
                logicalLength: Math.hypot(n4[0] - n3[0], n4[1] - n3[1]),
                vertexStartIdx: 0,
                vertexCount: 2,
                direction: direction,
                isHoriz: !extrudeVertically,
                steps: 0
        )
    } else if (featureType == 'pyramid') {
        // Pyramid: stairUp, flat top, stairDown
        int stairUpVerts = steps > 0 ? (steps + 1) * 2 : 2
        int stairDownVerts = steps > 0 ? (steps + 1) * 2 : 2

        newEdges << new LogicalEdge(
                type: 'diagonal',
                logicalStart: [n1[0], n1[1]],
                logicalEnd: [n2[0], n2[1]],
                logicalLength: Math.hypot(n2[0] - n1[0], n2[1] - n1[1]),
                vertexStartIdx: 0,
                vertexCount: stairUpVerts - 1,
                direction: direction,
                isHoriz: extrudeVertically,
                steps: steps
        )
        newEdges << new LogicalEdge(
                type: 'orthogonal',
                logicalStart: [n2[0], n2[1]],
                logicalEnd: [n3[0], n3[1]],
                logicalLength: Math.hypot(n3[0] - n2[0], n3[1] - n2[1]),
                vertexStartIdx: 0,
                vertexCount: 1,
                direction: direction,
                isHoriz: extrudeVertically,
                steps: 0
        )
        newEdges << new LogicalEdge(
                type: 'diagonal',
                logicalStart: [n3[0], n3[1]],
                logicalEnd: [n4[0], n4[1]],
                logicalLength: Math.hypot(n4[0] - n3[0], n4[1] - n3[1]),
                vertexStartIdx: 0,
                vertexCount: stairDownVerts - 1,
                direction: direction,
                isHoriz: extrudeVertically,
                steps: steps
        )
    } else if (featureType == 'round') {
        // Round: two curve sections meeting at peak
        int curveVerts = steps > 0 ? (steps + 1) * 2 : 2

        newEdges << new LogicalEdge(
                type: 'curve',
                logicalStart: [n1[0], n1[1]],
                logicalEnd: [n2[0], n2[1]],
                logicalLength: Math.hypot(n2[0] - n1[0], n2[1] - n1[1]),
                vertexStartIdx: 0,
                vertexCount: curveVerts - 1,
                direction: direction,
                isHoriz: extrudeVertically,
                steps: steps
        )
        newEdges << new LogicalEdge(
                type: 'curve',
                logicalStart: [n3[0], n3[1]],
                logicalEnd: [n4[0], n4[1]],
                logicalLength: Math.hypot(n4[0] - n3[0], n4[1] - n3[1]),
                vertexStartIdx: 0,
                vertexCount: curveVerts - 1,
                direction: direction,
                isHoriz: extrudeVertically,
                steps: steps
        )
    }

    return newEdges
}

/**
 * Update the edge registry after splicing a feature.
 * Splits the target edge and inserts new feature edges.
 */
def updateRegistry = { EdgeRegistry registry, int edgeIndex, LogicalEdge originalEdge,
                       double t1, double t2, List<Long> splitPt1, List<Long> splitPt2,
                       List<LogicalEdge> featureEdges, int removedVertexCount, int addedVertexCount ->

    def replacementEdges = []

    // 1. Portion of original edge before the feature (if any)
    if (t1 > 0.01) {
        double remainingFraction = t1
        int remainingVerts = Math.max(1, (int)(originalEdge.vertexCount * remainingFraction))

        replacementEdges << new LogicalEdge(
                type: originalEdge.type,
                logicalStart: originalEdge.logicalStart,
                logicalEnd: splitPt1,
                logicalLength: originalEdge.logicalLength * t1,
                vertexStartIdx: 0,  // Will be reindexed
                vertexCount: remainingVerts,
                direction: originalEdge.direction,
                isHoriz: originalEdge.isHoriz,
                steps: Math.max(0, (int)(originalEdge.steps * t1))
        )
    }

    // 2. The feature edges
    replacementEdges.addAll(featureEdges)

    // 3. Portion of original edge after the feature (if any)
    if (t2 < 0.99) {
        double remainingFraction = 1.0 - t2
        int remainingVerts = Math.max(1, (int)(originalEdge.vertexCount * remainingFraction))

        replacementEdges << new LogicalEdge(
                type: originalEdge.type,
                logicalStart: splitPt2,
                logicalEnd: originalEdge.logicalEnd,
                logicalLength: originalEdge.logicalLength * (1.0 - t2),
                vertexStartIdx: 0,
                vertexCount: remainingVerts,
                direction: originalEdge.direction,
                isHoriz: originalEdge.isHoriz,
                steps: Math.max(0, (int)(originalEdge.steps * (1.0 - t2)))
        )
    }

    // Replace the original edge with the new edges
    registry.edges.remove(edgeIndex)
    registry.edges.addAll(edgeIndex, replacementEdges)

    // Reindex all edges
    registry.reindexAll()
}

// ============================================================
// 6. SPATIAL INDEX (for collision detection)
// ============================================================

class SpatialIndex {
    int cellSize = 5000
    Map<String, List<Integer>> grid = [:]

    def rebuild(List vertices) {
        grid.clear()
        int n = vertices.size()
        for (int i = 0; i < n; i++) {
            def v1 = vertices[i]
            def v2 = vertices[(i + 1) % n]
            int minX = (Math.min(v1[0], v2[0]) / cellSize).toInteger()
            int maxX = (Math.max(v1[0], v2[0]) / cellSize).toInteger()
            int minY = (Math.min(v1[1], v2[1]) / cellSize).toInteger()
            int maxY = (Math.max(v1[1], v2[1]) / cellSize).toInteger()
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    def key = "${x}_${y}"
                    if (!grid.containsKey(key)) grid[key] = []
                    grid[key] << i
                }
            }
        }
    }

    Set<Integer> getCandidates(long x1, long y1, long x2, long y2, long padding) {
        Set<Integer> c = new HashSet<>()
        int minX = ((Math.min(x1, x2) - padding) / cellSize).toInteger()
        int maxX = ((Math.max(x1, x2) + padding) / cellSize).toInteger()
        int minY = ((Math.min(y1, y2) - padding) / cellSize).toInteger()
        int maxY = ((Math.max(y1, y2) + padding) / cellSize).toInteger()
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                def key = "${x}_${y}"
                if (grid.containsKey(key)) c.addAll(grid[key])
            }
        }
        return c
    }
}

// ============================================================
// 7. BASE SHAPES
// ============================================================
def vertices = []
def X0 = MIN_BOUND; def X1 = MIN_BOUND + THICKNESS
def X2 = MAX_BOUND - THICKNESS; def X3 = MAX_BOUND
def Y0 = MIN_BOUND; def Y1 = MIN_BOUND + THICKNESS
def Y2 = MAX_BOUND - THICKNESS; def Y3 = MAX_BOUND
def XC1 = 50000 - (THICKNESS / 2).toInteger(); def XC2 = 50000 + (THICKNESS / 2).toInteger()
def YC1 = 50000 - (THICKNESS / 2).toInteger(); def YC2 = 50000 + (THICKNESS / 2).toInteger()

switch (shapeType) {
    case 'q': vertices = [[X0, Y0], [X3, Y0], [X3, Y3], [X0, Y3]]; break
    case 'l': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y3], [X0, Y3]]; break
    case 'c': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y2], [X3, Y2], [X3, Y3], [X0, Y3]]; break
    case 'h': vertices = [[X0, Y0], [X1, Y0], [X1, YC1], [X2, YC1], [X2, Y0], [X3, Y0], [X3, Y3], [X2, Y3], [X2, YC2], [X1, YC2], [X1, Y3], [X0, Y3]]; break
    case 't': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [XC2, Y1], [XC2, Y3], [XC1, Y3], [XC1, Y1], [X0, Y1]]; break
    case 'x': vertices = [[XC1, Y0], [XC2, Y0], [XC2, YC1], [X3, YC1], [X3, YC2], [XC2, YC2], [XC2, Y3], [XC1, Y3], [XC1, YC2], [X0, YC2], [X0, YC1], [XC1, YC1]]; break
    case 's': vertices = [[X1, Y0], [X3, Y0], [X3, YC2], [XC2, YC2], [XC2, Y3], [X0, Y3], [X0, YC1], [X1, YC1]]; break
}

// Initialize the edge registry
def edgeRegistry = new EdgeRegistry()
edgeRegistry.initializeFromVertices(vertices)

// ============================================================
// 8. GENERATION LOOP
// ============================================================
def spatialIndex = new SpatialIndex()
spatialIndex.rebuild(vertices)

phases.each { phase ->
    System.err.println "Starting Phase: ${phase.name} (Vertices: ${vertices.size()}, Edges: ${edgeRegistry.edges.size()})"
    int modificationsMade = 0
    int totalFails = 0
    int maxTotalFails = (phase.name == "Micro") ? 5000 : 1500

    // Diagnostics
    def featureTypeCounts = [box: 0, pyramid: 0, round: 0]
    int rejectedOrthogonal = 0
    int rejectedProximity = 0
    int rejectedJTS = 0
    int rejectedFinalOrthogonal = 0

    while (totalFails < maxTotalFails) {
        if (phase.count != -1 && modificationsMade >= phase.count) break
        if (vertices.size() >= TARGET_VERTICES) break
        if (vertices.size() % 50 == 0) System.err.print("\rVertices: ${vertices.size()}/${TARGET_VERTICES} ")

        // Select edge using logical length (allows selection of diagonal/curve edges)
        LogicalEdge selectedEdge = edgeRegistry.selectWeightedEdge(phase.minLen * 1.5, rnd)
        if (selectedEdge == null) {
            totalFails++
            continue
        }

        boolean successOnEdge = false
        int edgeRetries = 0

        while (edgeRetries < 5 && !successOnEdge) {
            edgeRetries++

            // Calculate feature dimensions
            long segmentLen = rnd.nextInt((int)(phase.maxLen - phase.minLen)) + phase.minLen
            if (segmentLen >= selectedEdge.logicalLength - 100) {
                segmentLen = (long)(selectedEdge.logicalLength * 0.8)
            }

            // --- NEW CODE START: ASPECT RATIO CLAMP ---
            // "Fjord Protection": This ensures the mouth of the feature is wide enough
            // relative to how deep it goes.
            // A ratio of 1.5 means depth cannot exceed 1.5x the width.
            // Lower this number (e.g. 1.0) for even "stouter" features.
            double maxAspectRatio = 1.5
            long maxAllowedDepth = (long)(segmentLen * maxAspectRatio)

            long rawDepth = rnd.nextInt((int)(phase.maxDepth - phase.minDepth)) + phase.minDepth
            long depth = Math.min(rawDepth, maxAllowedDepth)

            // If the enforced aspect ratio makes the feature too shallow for this phase,
            // we skip it to avoid cluttering the map with tiny, flat bumps.
            if (depth < phase.minDepth) continue
            // --- NEW CODE END ---

            // Parametric positions along the logical edge
            double availableLen = selectedEdge.logicalLength - segmentLen
            // ... rest of the loop continues as normal ...
            if (availableLen < 100) {
                continue
            }
            double offsetFraction = rnd.nextDouble() * (availableLen / selectedEdge.logicalLength)
            double t1 = offsetFraction
            double t2 = offsetFraction + (segmentLen / selectedEdge.logicalLength)

            if (t1 < 0.01) t1 = 0.01
            if (t2 > 0.99) t2 = 0.99
            if (t2 <= t1 + 0.05) continue

            depth = rnd.nextInt((int)(phase.maxDepth - phase.minDepth)) + phase.minDepth
            int direction = rnd.nextBoolean() ? 1 : -1
            String featureType = phase.types[rnd.nextInt(phase.types.size())]

            // Find attachment points on the actual geometry
            def (startIdx, startNeedsSplit, startPt) = selectedEdge.findAttachmentPoint(vertices, t1)
            def (endIdx, endNeedsSplit, endPt) = selectedEdge.findAttachmentPoint(vertices, t2)

            if (startIdx >= endIdx && !startNeedsSplit && !endNeedsSplit) {
                continue  // Degenerate case
            }

            // Calculate extrusion direction - MUST be axis-aligned for orthogonal output
            // Determine the dominant direction of the logical edge
            long edgeDx = selectedEdge.logicalEnd[0] - selectedEdge.logicalStart[0]
            long edgeDy = selectedEdge.logicalEnd[1] - selectedEdge.logicalStart[1]

            // Choose perpendicular direction: if edge is more horizontal, go vertical; vice versa
            boolean extrudeVertically = Math.abs(edgeDx) >= Math.abs(edgeDy)

            // CRITICAL: Snap attachment points to ensure orthogonal geometry
            // If extruding vertically, both points must share same Y coordinate (they span X)
            // If extruding horizontally, both points must share same X coordinate (they span Y)
            def n1, n4
            if (extrudeVertically) {
                // Feature spans horizontally, extrudes vertically
                // Align Y coordinates to startPt's Y
                n1 = [startPt[0], startPt[1]]
                n4 = [endPt[0], startPt[1]]  // Force same Y as n1
            } else {
                // Feature spans vertically, extrudes horizontally
                // Align X coordinates to startPt's X
                n1 = [startPt[0], startPt[1]]
                n4 = [startPt[0], endPt[1]]  // Force same X as n1
            }

            // Calculate extrusion endpoints
            def n2, n3
            if (extrudeVertically) {
                n2 = [n1[0], n1[1] + depth * direction]
                n3 = [n4[0], n4[1] + depth * direction]
            } else {
                n2 = [n1[0] + depth * direction, n1[1]]
                n3 = [n4[0] + depth * direction, n4[1]]
            }

            // Recalculate segment length based on snapped points
            long actualSegmentLen = extrudeVertically ?
                    Math.abs(n4[0] - n1[0]) : Math.abs(n4[1] - n1[1])
            if (actualSegmentLen < phase.minLen * 0.5) continue  // Too short after snapping

            // Generate feature vertices based on type - ALL paths must be orthogonal
            def featureVerts = []
            int steps = phase.steps

            if (featureType == 'box') {
                // Box: n1 -> n2 -> n3 -> n4, but each leg must be orthogonal
                featureVerts = makeOrthogonalPath([n1, n2, n3, n4])
            } else if (featureType == 'pyramid') {
                double shrink = 0.25
                long sAmt = (long)(segmentLen * shrink)
                def top_s, top_e

                if (extrudeVertically) {
                    // Top edge shrinks horizontally
                    long xDir = (long)Math.signum(n3[0] - n2[0])
                    top_s = [n2[0] + sAmt * xDir, n2[1]]
                    top_e = [n3[0] - sAmt * xDir, n3[1]]
                } else {
                    // Top edge shrinks vertically
                    long yDir = (long)Math.signum(n3[1] - n2[1])
                    top_s = [n2[0], n2[1] + sAmt * yDir]
                    top_e = [n3[0], n3[1] - sAmt * yDir]
                }

                def stairUp = generateLinearStairVertices(n1, top_s, steps)
                def stairDown = generateLinearStairVertices(top_e, n4, steps)

                // Combine: stairUp ends at top_s, then top_e, then stairDown (without its first point which is top_e)
                featureVerts = stairUp + [top_e] + stairDown.drop(1)
                featureVerts = featureVerts.unique { a, b -> pointsEqual(a, b) ? 0 : 1 }
            } else if (featureType == 'round') {
                // Round features meet at a peak
                def midTop = [((n2[0] + n3[0]) / 2) as long, ((n2[1] + n3[1]) / 2) as long]

                // isHoriz for stair generation: based on extrusion direction
                boolean stairIsHoriz = extrudeVertically

                def stairUp = generateRoundStairVertices(n1, midTop, steps, stairIsHoriz)
                def stairDown = generateRoundStairVertices(midTop, n4, steps, stairIsHoriz)

                featureVerts = stairUp + stairDown.drop(1)
                featureVerts = featureVerts.unique { a, b -> pointsEqual(a, b) ? 0 : 1 }
            }

            if (featureVerts.size() < 2) continue

            // CRITICAL: Verify all feature edges are orthogonal (no diagonals)
            boolean allOrthogonal = true
            for (int i = 0; i < featureVerts.size() - 1; i++) {
                def fv1 = featureVerts[i]
                def fv2 = featureVerts[i + 1]
                if (fv1[0] != fv2[0] && fv1[1] != fv2[1]) {
                    allOrthogonal = false
                    break
                }
            }
            if (!allOrthogonal) {
                rejectedOrthogonal++
                continue  // Reject features with diagonal edges
            }

            // Calculate bounding box for spatial index lookup
            def allFeaturePts = featureVerts + [n1, n2, n3, n4]
            long minX = allFeaturePts.collect { it[0] }.min()
            long maxX = allFeaturePts.collect { it[0] }.max()
            long minY = allFeaturePts.collect { it[1] }.min()
            long maxY = allFeaturePts.collect { it[1] }.max()

            Set<Integer> candidates = spatialIndex.getCandidates(minX, minY, maxX, maxY, phase.minGap)

            // Quick proximity check: ensure feature points aren't too close to existing edges
            boolean proximityOk = true
            int edgeStartIdx = selectedEdge.vertexStartIdx
            int edgeEndIdx = edgeStartIdx + selectedEdge.vertexCount

            for (int ci : candidates) {
                // Skip edges that are part of the edge we're modifying
                if (ci >= edgeStartIdx && ci <= edgeEndIdx) continue

                def cv1 = vertices[ci]
                def cv2 = vertices[(ci + 1) % vertices.size()]

                // Check each feature point against this candidate edge
                for (def fp : featureVerts) {
                    double dist = java.awt.geom.Line2D.ptSegDist(
                            cv1[0] as double, cv1[1] as double,
                            cv2[0] as double, cv2[1] as double,
                            fp[0] as double, fp[1] as double
                    )
                    if (dist < phase.minGap && dist > 1) {
                        proximityOk = false
                        break
                    }
                }
                if (!proximityOk) break

                // Check candidate vertices against feature edges
                for (int fi = 0; fi < featureVerts.size() - 1; fi++) {
                    def fv1 = featureVerts[fi]
                    def fv2 = featureVerts[fi + 1]
                    double dist = java.awt.geom.Line2D.ptSegDist(
                            fv1[0] as double, fv1[1] as double,
                            fv2[0] as double, fv2[1] as double,
                            cv1[0] as double, cv1[1] as double
                    )
                    if (dist < phase.minGap && dist > 1) {
                        proximityOk = false
                        break
                    }
                }
                if (!proximityOk) break
            }

            if (!proximityOk) {
                rejectedProximity++
                continue
            }

            // Splice the feature into the vertex list
            def tempVertices = spliceFeature(vertices, startIdx, startNeedsSplit, startPt,
                    endIdx, endNeedsSplit, endPt, featureVerts)

            // Validate with JTS - check both validity AND simplicity (no self-intersections)
            Polygon jtsPoly = toJTSPolygon(tempVertices, gf)
            boolean isValidPoly = (jtsPoly != null && jtsPoly.isValid() && jtsPoly.isSimple())

            // Additional check: verify the exterior ring doesn't self-intersect
            if (isValidPoly) {
                def ring = jtsPoly.getExteriorRing()
                isValidPoly = ring.isSimple()
            }

            if (!isValidPoly) {
                rejectedJTS++
            }

            // CRITICAL: Verify the entire resulting polygon has only orthogonal edges
            if (isValidPoly) {
                for (int i = 0; i < tempVertices.size(); i++) {
                    def tv1 = tempVertices[i]
                    def tv2 = tempVertices[(i + 1) % tempVertices.size()]
                    if (tv1[0] != tv2[0] && tv1[1] != tv2[1]) {
                        isValidPoly = false
                        rejectedFinalOrthogonal++
                        break
                    }
                }
            }

            if (isValidPoly) {
                // Success! Track feature type
                featureTypeCounts[featureType]++

                // Update everything
                int oldVertexCount = vertices.size()
                vertices = tempVertices
                int newVertexCount = vertices.size()

                // Find the edge index
                int edgeIdx = edgeRegistry.edges.indexOf(selectedEdge)
                if (edgeIdx >= 0) {
                    // Create new logical edges for the feature
                    def featureEdges = createFeatureEdges(featureType, n1, n2, n3, n4,
                            steps, extrudeVertically, direction, featureVerts)

                    // Recalculate vertex counts for feature edges based on actual vertices
                    int featureVertCount = featureVerts.size()
                    int vertsPerEdge = Math.max(1, featureVertCount / featureEdges.size())
                    int remaining = featureVertCount
                    for (int i = 0; i < featureEdges.size(); i++) {
                        if (i == featureEdges.size() - 1) {
                            featureEdges[i].vertexCount = remaining
                        } else {
                            featureEdges[i].vertexCount = vertsPerEdge
                            remaining -= vertsPerEdge
                        }
                    }

                    // Update the registry
                    updateRegistry(edgeRegistry, edgeIdx, selectedEdge, t1, t2,
                            startPt, endPt, featureEdges,
                            oldVertexCount - newVertexCount, featureVerts.size())
                }

                spatialIndex.rebuild(vertices)
                modificationsMade++
                successOnEdge = true
                totalFails = 0
            }
        }

        if (!successOnEdge) totalFails++
    }
    System.err.println "\nPhase ${phase.name} complete. Edges: ${edgeRegistry.edges.size()}"
    System.err.println "  Features: box=${featureTypeCounts.box}, pyramid=${featureTypeCounts.pyramid}, round=${featureTypeCounts.round}"
    System.err.println "  Rejected: orthogonal=${rejectedOrthogonal}, proximity=${rejectedProximity}, JTS=${rejectedJTS}, finalOrth=${rejectedFinalOrthogonal}"
}

// File Names
def baseName = "logical_${shapeType}_${currentPalette.name.toLowerCase()}"
def csvFile, imgFile
int fileCounter = 0
while (true) {
    def suffix = (fileCounter == 0) ? "" : "_${fileCounter}"
    def csvName = "${baseName}${suffix}.csv"
    def imgName = "${baseName}${suffix}.png"
    csvFile = new File(csvName); imgFile = new File(imgName)
    if (!csvFile.exists() && !imgFile.exists()) break
    fileCounter++
}

System.err.println "Finished! Vertices: ${vertices.size()}"
System.err.println "Saving coordinates to ${csvFile.name}..."
csvFile.withWriter { w -> vertices.each { w.writeLine("${it[0]},${it[1]}") } }

// ============================================================
// 9. RENDER ENGINE
// ============================================================
System.err.println "Generating Image to ${imgFile.name}..."
try {
    def img = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB)
    def g2d = img.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    // 1. Background
    def center = new Point2D.Float(IMAGE_SIZE / 2 as float, IMAGE_SIZE / 2 as float)
    float radius = IMAGE_SIZE * 0.8f
    float[] dist = [0.0f, 1.0f]
    Color[] colorsArr = [currentPalette.bg.brighter(), currentPalette.bg]
    RadialGradientPaint bgPaint = new RadialGradientPaint(center, radius, dist, colorsArr)
    g2d.setPaint(bgPaint)
    g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE)

    double sc = IMAGE_SIZE / CANVAS_SIZE

    // 2. Create Path
    Path2D poly = new Path2D.Double()
    vertices.eachWithIndex { v, i ->
        double x = v[0] * sc
        double y = v[1] * sc
        if (i == 0) poly.moveTo(x, y) else poly.lineTo(x, y)
    }
    poly.closePath()

    // 3. Unified Fill (Vertical Gradient)
    Rectangle bounds = poly.getBounds()
    GradientPaint fillGrad = new GradientPaint(
            (float) bounds.getCenterX(), (float) bounds.getMinY(), currentPalette.fill1,
            (float) bounds.getCenterX(), (float) bounds.getMaxY(), currentPalette.fill2
    )
    g2d.setPaint(fillGrad)
    g2d.fill(poly)

    // 4. Scanlines
    g2d.setClip(poly)
    g2d.setColor(new Color(currentPalette.glow.getRed(), currentPalette.glow.getGreen(), currentPalette.glow.getBlue(), 40))
    int scanSpacing = (IMAGE_SIZE / 200).toInteger()
    for (int i = 0; i < IMAGE_SIZE; i += scanSpacing) {
        g2d.drawLine(0, i, IMAGE_SIZE, i)
    }
    g2d.setClip(null)

    // 5. Glow Outline
    float strokeWidth = (float) (1000 * sc)
    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    g2d.setColor(new Color(currentPalette.glow.getRed(), currentPalette.glow.getGreen(), currentPalette.glow.getBlue(), 60))
    g2d.draw(poly)

    g2d.setStroke(new BasicStroke((float) (strokeWidth * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    g2d.setColor(currentPalette.glow)
    g2d.draw(poly)

    // 6. UI Decorations
    g2d.setColor(currentPalette.accent)
    g2d.setFont(new Font("Monospaced", Font.BOLD, 40))
    g2d.drawString("ENTITY: ${shapeType.toUpperCase()}-CLASS FRACTAL", 100, 80)
    g2d.setFont(new Font("Monospaced", Font.PLAIN, 24))
    g2d.drawString("VERTICES: ${vertices.size()} // PALETTE: ${currentPalette.name.toUpperCase()} // LOGICAL EDGES: ${edgeRegistry.edges.size()}", 100, 120)

    int cLen = 150
    int pad = 50
    g2d.setStroke(new BasicStroke(6.0f))
    g2d.drawLine(pad, pad, pad + cLen, pad)
    g2d.drawLine(pad, pad, pad, pad + cLen)
    g2d.drawLine(IMAGE_SIZE - pad, pad, IMAGE_SIZE - pad - cLen, pad)
    g2d.drawLine(IMAGE_SIZE - pad, pad, IMAGE_SIZE - pad, pad + cLen)
    g2d.drawLine(pad, IMAGE_SIZE - pad, pad + cLen, IMAGE_SIZE - pad)
    g2d.drawLine(pad, IMAGE_SIZE - pad, pad, IMAGE_SIZE - pad - cLen)
    g2d.drawLine(IMAGE_SIZE - pad, IMAGE_SIZE - pad, IMAGE_SIZE - pad - cLen, IMAGE_SIZE - pad)
    g2d.drawLine(IMAGE_SIZE - pad, IMAGE_SIZE - pad, IMAGE_SIZE - pad, IMAGE_SIZE - pad - cLen)

    g2d.dispose()
    ImageIO.write(img, "PNG", imgFile)
    System.err.println "Done."
} catch (e) {
    e.printStackTrace()
}