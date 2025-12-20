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
import java.awt.geom.Line2D
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
// UPDATED: Increased contrast between fill1 and fill2 for a more visible gradient.
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
        [name: "Macro", count: 8, minLen: 8000, maxLen: 22000, minDepth: 4000, maxDepth: 10000, steps: 6, minGap: 4000, types: ["pyramid", "pyramid", "round", "round", "box"]],
        [name: "Meso-Major", count: 30, minLen: 3000, maxLen: 9000, minDepth: 2000, maxDepth: 5000, steps: 4, minGap: 2000, types: ["pyramid", "pyramid", "round", "round", "box"]],
        [name: "Meso-Minor", count: 100, minLen: 1200, maxLen: 3500, minDepth: 600, maxDepth: 2500, steps: 2, minGap: 800, types: ["box", "box", "pyramid", "round"]],
        [name: "Micro", count: -1, minLen: 300, maxLen: 1200, minDepth: 150, maxDepth: 1000, steps: 0, minGap: 250, types: ["box", "pyramid"]]
]

// ============================================================
// 4. GEOMETRY HELPERS (JTS & Spatial Index)
// ============================================================

// Initialize JTS Geometry Factory
def gf = new GeometryFactory()

// Helper to convert our list of lists [[x,y],...] to a JTS Polygon
def toJTSPolygon(List<List<Long>> verts, GeometryFactory factory) {
    if (verts.isEmpty()) return null
    Coordinate[] coords = new Coordinate[verts.size() + 1]
    for (int i = 0; i < verts.size(); i++) {
        coords[i] = new Coordinate(verts[i][0] as double, verts[i][1] as double)
    }
    // Close the ring
    coords[verts.size()] = coords[0]
    LinearRing shell = factory.createLinearRing(new CoordinateArraySequence(coords))
    return factory.createPolygon(shell, null)
}


class SpatialIndex {
    int cellSize = 5000; Map<String, List<Integer>> grid = [:]
    def rebuild(List vertices) {
        grid.clear(); int n = vertices.size()
        for (int i = 0; i < n; i++) {
            def v1 = vertices[i]; def v2 = vertices[(i + 1) % n]
            int minX = (Math.min(v1[0], v2[0])/cellSize).toInteger(); int maxX = (Math.max(v1[0], v2[0])/cellSize).toInteger()
            int minY = (Math.min(v1[1], v2[1])/cellSize).toInteger(); int maxY = (Math.max(v1[1], v2[1])/cellSize).toInteger()
            for (int x = minX; x <= maxX; x++) { for (int y = minY; y <= maxY; y++) {
                def key = "${x}_${y}"; if (!grid.containsKey(key)) grid[key] = []; grid[key] << i
            }}
        }
    }
    Set<Integer> getCandidates(long x1, long y1, long x2, long y2, long padding) {
        Set<Integer> c = new HashSet<>()
        int minX = ((Math.min(x1, x2)-padding)/cellSize).toInteger(); int maxX = ((Math.max(x1, x2)+padding)/cellSize).toInteger()
        int minY = ((Math.min(y1, y2)-padding)/cellSize).toInteger(); int maxY = ((Math.max(y1, y2)+padding)/cellSize).toInteger()
        for (int x = minX; x <= maxX; x++) { for (int y = minY; y <= maxY; y++) {
            def key = "${x}_${y}"; if (grid.containsKey(key)) c.addAll(grid[key])
        }}
        return c
    }
}

def linesIntersect = { p1, p2, p3, p4 -> Line2D.linesIntersect(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1]) }

def makeSteps = { pts ->
    def res = [];
    for (int i=0; i<pts.size()-1; i++) {
        def p1 = pts[i]; def p2 = pts[i+1]; res << p1
        if (p1[0] != p2[0] && p1[1] != p2[1]) res << [p1[0], p2[1]]
    }
    res << pts[-1]; return res
}

def generateLinearStairs = { start, end, steps ->
    if (steps <= 0) return makeSteps([start, end])
    def targets = []; long dx = end[0] - start[0]; long dy = end[1] - start[1]
    for (int i = 0; i <= steps+1; i++) {
        double t = i / (double)(steps+1); targets << [start[0] + dx*t, start[1] + dy*t]
    }
    return makeSteps(targets)
}

def generateRoundStairs = { start, end, steps, isHoriz ->
    if (steps <= 0) return makeSteps([start, end])
    def targets = []; long dx_total = end[0] - start[0]; long dy_total = end[1] - start[1]
    for (int i = 0; i <= steps+1; i++) {
        double t = i / (double)(steps+1); double angle = t * (Math.PI / 2.0)
        double dx = Math.sin(angle); double dy = 1.0 - Math.cos(angle)
        long tx, ty
        if (isHoriz) { tx = start[0] + (long)(dx_total * dx); ty = start[1] + (long)(dy_total * dy) }
        else { tx = start[0] + (long)(dx_total * dy); ty = start[1] + (long)(dy_total * dx) }
        targets << [tx, ty]
    }
    return makeSteps(targets)
}

def selectWeightedEdge = { verts ->
    def n = verts.size(); double[] w = new double[n]; double sum = 0
    for (int i = 0; i < n; i++) {
        def p1 = verts[i]; def p2 = verts[(i + 1) % n]
        double len = (p1[0] - p2[0]).abs() + (p1[1] - p2[1]).abs(); w[i] = len; sum += len
    }
    double r = rnd.nextDouble() * sum; double acc = 0
    for (int i = 0; i < n; i++) { acc += w[i]; if (r <= acc) return [i, w[i]] }
    return [n-1, w[n-1]]
}

// Fast pre-check using bounding boxes
def checkBoundingBox = { newPoints, allVertices, changedEdgeIdx, minGap, candidates ->
    int n = allVertices.size()
    // Check new points against candidate edges
    for (int k = 1; k < newPoints.size() - 1; k++) {
        def np = newPoints[k]
        for (int i : candidates) {
            if (i == changedEdgeIdx) continue
            def v1 = allVertices[i]; def v2 = allVertices[(i + 1) % n]
            if (Line2D.ptSegDist(v1[0], v1[1], v2[0], v2[1], np[0], np[1]) < minGap) return false
        }
    }
    // Check candidate points against new edges
    def newEdges = []; for (int i = 0; i < newPoints.size() - 1; i++) newEdges << [newPoints[i], newPoints[i+1]]
    for (int i : candidates) {
        if (i == changedEdgeIdx || i == (changedEdgeIdx + 1) % n) continue
        def oldP = allVertices[i]
        for (def newEdge : newEdges) {
            if (Line2D.ptSegDist(newEdge[0][0], newEdge[0][1], newEdge[1][0], newEdge[1][1], oldP[0], oldP[1]) < minGap) return false
        }
    }
    return true
}

// ============================================================
// 5. BASE SHAPES
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

// ============================================================
// 6. GENERATION LOOP (Robust)
// ============================================================
def spatialIndex = new SpatialIndex()
spatialIndex.rebuild(vertices)

phases.each { phase ->
    System.err.println "Starting Phase: ${phase.name} (Vertices: ${vertices.size()})"
    int modificationsMade = 0; int totalFails = 0
    // Increased fail limits due to stricter JTS checks
    int maxTotalFails = (phase.name == "Micro") ? 5000 : 1500

    while (totalFails < maxTotalFails) {
        if (phase.count != -1 && modificationsMade >= phase.count) break
        if (vertices.size() >= TARGET_VERTICES) break
        if (vertices.size() % 50 == 0) System.err.print("\rVertices: ${vertices.size()}/${TARGET_VERTICES} ")

        def selection = selectWeightedEdge(vertices)
        int idx = selection[0]; double edgeLen = selection[1]

        if (edgeLen < phase.minLen * 1.5) { totalFails++; continue }

        boolean successOnEdge = false; int edgeRetries = 0
        while (edgeRetries < 5 && !successOnEdge) {
            edgeRetries++
            long segmentLen = rnd.nextInt((int)(phase.maxLen - phase.minLen)) + phase.minLen
            if (segmentLen >= edgeLen - 100) segmentLen = (long)(edgeLen * 0.8)
            long offset = rnd.nextInt((int)(edgeLen - segmentLen))
            long depth = rnd.nextInt((int)(phase.maxDepth - phase.minDepth)) + phase.minDepth
            int direction = rnd.nextBoolean() ? 1 : -1
            String type = phase.types[rnd.nextInt(phase.types.size())]

            def p1 = vertices[idx]; def p2 = vertices[(idx + 1) % vertices.size()]
            boolean isHoriz = (p1[1] == p2[1])
            def n1, n4, rect_n2, rect_n3

            if (isHoriz) {
                long xB = Math.min(p1[0], p2[0]); long y = p1[1]
                n1 = [xB + offset, y]; n4 = [xB + offset + segmentLen, y]
                rect_n2 = [n1[0], y + (depth * direction)]; rect_n3 = [n4[0], y + (depth * direction)]
            } else {
                long yB = Math.min(p1[1], p2[1]); long x = p1[0]
                n1 = [x, yB + offset]; n4 = [x, yB + offset + segmentLen]
                rect_n2 = [x + (depth * direction), n1[1]]; rect_n3 = [x + (depth * direction), n4[1]]
            }

            def bbox = [n1, rect_n2, rect_n3, n4]
            long minX = bbox.collect{it[0]}.min(), maxX = bbox.collect{it[0]}.max()
            long minY = bbox.collect{it[1]}.min(), maxY = bbox.collect{it[1]}.max()
            Set<Integer> candidates = spatialIndex.getCandidates(minX, minY, maxX, maxY, phase.minGap)

            // 1. FAST PRE-CHECK (Bounding Box Proximity)
            if (checkBoundingBox(bbox, vertices, idx, phase.minGap, candidates)) {
                def pointsToAdd = []
                if (type == "box") { pointsToAdd = [n1, rect_n2, rect_n3, n4] }
                else {
                    int steps = phase.steps
                    if (steps > 0) {
                        double shrink = 0.25; long sAmt = (long)(segmentLen * shrink)
                        def top_s, top_e
                        if (isHoriz) { top_s=[rect_n2[0]+sAmt, rect_n2[1]]; top_e=[rect_n3[0]-sAmt, rect_n3[1]] }
                        else { top_s=[rect_n2[0], rect_n2[1]+sAmt]; top_e=[rect_n3[0], rect_n3[1]-sAmt] }
                        def sUp = (type == "round") ? generateRoundStairs(n1, top_s, steps, isHoriz) : generateLinearStairs(n1, top_s, steps)
                        def sDown = (type == "round") ? generateRoundStairs(top_e, n4, steps, isHoriz) : generateLinearStairs(top_e, n4, steps)
                        pointsToAdd.addAll(sUp); pointsToAdd.addAll(sDown); pointsToAdd = pointsToAdd.unique()
                    } else { pointsToAdd = [n1, rect_n2, rect_n3, n4] }
                }

                boolean rev = false
                if (isHoriz && p1[0] > p2[0]) rev = true
                if (!isHoriz && p1[1] > p2[1]) rev = true
                if (rev) pointsToAdd = pointsToAdd.reverse()

                // 2. ROBUST JTS VALIDATION
                // Create a temporary vertex list with the change applied
                def tempVertices = new ArrayList<>(vertices)
                tempVertices.addAll(idx + 1, pointsToAdd)

                // Convert to JTS Polygon and check validity
                Polygon jtsPoly = toJTSPolygon(tempVertices, gf)
                if (jtsPoly != null && jtsPoly.isValid()) {
                    // Success! The new shape is mathematically sound.
                    vertices = tempVertices
                    spatialIndex.rebuild(vertices)
                    modificationsMade++
                    successOnEdge = true
                    totalFails = 0
                } else {
                    // JTS found a self-intersection. Discard this attempt.
                    // (No need to increment totalFails here, the outer loop handles it)
                }
            }
        }
        if (!successOnEdge) totalFails++
    }
    System.err.println "\nPhase ${phase.name} complete."
}

// File Names
def baseName = "robust_${shapeType}_${currentPalette.name.toLowerCase()}"
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
// 7. RENDER ENGINE (Unified Fill, Glow, Scanlines)
// ============================================================
System.err.println "Generating Image to ${imgFile.name}..."
try {
    def img = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB)
    def g2d = img.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    // 1. Background
    def center = new Point2D.Float(IMAGE_SIZE/2, IMAGE_SIZE/2)
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
        double x = v[0] * sc; double y = v[1] * sc
        if(i==0) poly.moveTo(x, y) else poly.lineTo(x, y)
    }
    poly.closePath()

    // 3. UNIFIED FILL (Vertical Gradient)
    Rectangle bounds = poly.getBounds()
    // UPDATED: Changed to vertical gradient for better visibility
    GradientPaint fillGrad = new GradientPaint(
            (float)bounds.getCenterX(), (float)bounds.getMinY(), currentPalette.fill1,
            (float)bounds.getCenterX(), (float)bounds.getMaxY(), currentPalette.fill2
    )
    g2d.setPaint(fillGrad)
    g2d.fill(poly)

    // 4. SCANLINES
    g2d.setClip(poly)
    g2d.setColor(new Color(currentPalette.glow.getRed(), currentPalette.glow.getGreen(), currentPalette.glow.getBlue(), 40))
    int scanSpacing = (IMAGE_SIZE / 200).toInteger()
    for(int i=0; i<IMAGE_SIZE; i+=scanSpacing) {
        g2d.drawLine(0, i, IMAGE_SIZE, i)
    }
    g2d.setClip(null)

    // 5. GLOW OUTLINE
    float strokeWidth = (float)(1000 * sc)
    // UPDATED: Added explicit (float) casts to satisfy BasicStroke requirements
    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    g2d.setColor(new Color(currentPalette.glow.getRed(), currentPalette.glow.getGreen(), currentPalette.glow.getBlue(), 60))
    g2d.draw(poly)

    g2d.setStroke(new BasicStroke((float)(strokeWidth * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
    g2d.setColor(currentPalette.glow)
    g2d.draw(poly)

    // 6. UI DECORATIONS
    g2d.setColor(currentPalette.accent)
    g2d.setFont(new Font("Monospaced", Font.BOLD, 40))
    g2d.drawString("ENTITY: ${shapeType.toUpperCase()}-CLASS FRACTAL", 100, 80)
    g2d.setFont(new Font("Monospaced", Font.PLAIN, 24))
    g2d.drawString("VERTICES: ${vertices.size()} // PALETTE: ${currentPalette.name.toUpperCase()} // JTS VALIDATED", 100, 120)

    int cLen = 150; int pad = 50
    g2d.setStroke(new BasicStroke(6.0f))
    g2d.drawLine(pad, pad, pad+cLen, pad); g2d.drawLine(pad, pad, pad, pad+cLen)
    g2d.drawLine(IMAGE_SIZE-pad, pad, IMAGE_SIZE-pad-cLen, pad); g2d.drawLine(IMAGE_SIZE-pad, pad, IMAGE_SIZE-pad, pad+cLen)
    g2d.drawLine(pad, IMAGE_SIZE-pad, pad+cLen, IMAGE_SIZE-pad); g2d.drawLine(pad, IMAGE_SIZE-pad, pad, IMAGE_SIZE-pad-cLen)
    g2d.drawLine(IMAGE_SIZE-pad, IMAGE_SIZE-pad, IMAGE_SIZE-pad-cLen, IMAGE_SIZE-pad); g2d.drawLine(IMAGE_SIZE-pad, IMAGE_SIZE-pad, IMAGE_SIZE-pad, IMAGE_SIZE-pad-cLen)

    g2d.dispose()
    ImageIO.write(img, "PNG", imgFile)
    System.err.println "Done."
} catch (e) { e.printStackTrace() }