import java.awt.geom.Line2D
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.Color

// ============================================================
// CONFIGURATION
// ============================================================
def TARGET_VERTICES = 2500
def CANVAS_SIZE = 100000
def IMAGE_SIZE = 10000
def MIN_BOUND = 30000
def MAX_BOUND = 70000
def THICKNESS = 12000
def RANDOM_SEED = System.currentTimeMillis()

// Argument parsing
def shapeType = 'q'
if (args.any { it == '-l' }) shapeType = 'l'
if (args.any { it == '-h' }) shapeType = 'h'
if (args.any { it == '-c' }) shapeType = 'c'
if (args.any { it == '-q' }) shapeType = 'q'
def argCount = args.find { it.isNumber() }
if (argCount) TARGET_VERTICES = argCount.toInteger()

// 4-PHASE CONFIGURATION
def phases = [
        // ---------------------------------------------------------
        // PHASE 1: MACRO (The Continent Builders)
        // ---------------------------------------------------------
        // Creates massive protrusions.
        // Heavy on Pyramids and Domes (Natural look).
        [name: "Macro", count: 8, minLen: 8000, maxLen: 20000, minDepth: 3000, maxDepth: 8000, steps: 5, minGap: 4000,
         types: ["pyramid", "pyramid", "round", "round", "box"]],

        // ---------------------------------------------------------
        // PHASE 2: MESO-MAJOR (The Branching)
        // ---------------------------------------------------------
        // NEW PHASE. Large features that build upon the Macro structure.
        // By targeting long edges (minLen 3000), it naturally picks the
        // "flanks" of Phase 1 features, creating perpendicular branches.
        // Uses same diverse geometry types as Macro.
        [name: "Meso-Major", count: 25, minLen: 3000, maxLen: 9000, minDepth: 2000, maxDepth: 5000, steps: 4, minGap: 2000,
         types: ["pyramid", "pyramid", "round", "round", "box"]],

        // ---------------------------------------------------------
        // PHASE 3: MESO-MINOR (The Texture/Buildings)
        // ---------------------------------------------------------
        // Smaller features, mostly rectangular "boxes" to simulate
        // buildings or machinery on the coast.
        [name: "Meso-Minor", count: 80, minLen: 1500, maxLen: 4000, minDepth: 600, maxDepth: 2500, steps: 2, minGap: 800,
         types: ["box", "box", "pyramid", "round"]],

        // ---------------------------------------------------------
        // PHASE 4: MICRO (The Grit)
        // ---------------------------------------------------------
        // Pure noise to reach vertex count. Small gaps allowed.
        [name: "Micro", count: -1, minLen: 300, maxLen: 1200, minDepth: 100, maxDepth: 1000, steps: 0, minGap: 300,
         types: ["box", "pyramid"]]
]

// ------------------------------------------------------------
// SPATIAL INDEX
// ------------------------------------------------------------
class SpatialIndex {
    int cellSize = 5000
    Map<String, List<Integer>> grid = [:]

    def rebuild(List vertices) {
        grid.clear()
        int n = vertices.size()
        for (int i = 0; i < n; i++) {
            def v1 = vertices[i]; def v2 = vertices[(i + 1) % n]
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
        Set<Integer> candidates = new HashSet<>()
        int minX = ((Math.min(x1, x2) - padding) / cellSize).toInteger()
        int maxX = ((Math.max(x1, x2) + padding) / cellSize).toInteger()
        int minY = ((Math.min(y1, y2) - padding) / cellSize).toInteger()
        int maxY = ((Math.max(y1, y2) + padding) / cellSize).toInteger()
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                def key = "${x}_${y}"
                if (grid.containsKey(key)) candidates.addAll(grid[key])
            }
        }
        return candidates
    }
}

// ------------------------------------------------------------
// FILE NAMING
// ------------------------------------------------------------
def baseName = "polygon_${shapeType}"
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
System.err.println "Generating ${shapeType.toUpperCase()} with seed: $RANDOM_SEED"
System.err.println "Output: ${csvFile.name}"

// ============================================================
// BASE SHAPES
// ============================================================
def vertices = []
def X0 = MIN_BOUND; def X1 = MIN_BOUND + THICKNESS
def X2 = MAX_BOUND - THICKNESS; def X3 = MAX_BOUND
def Y0 = MIN_BOUND; def Y1 = MIN_BOUND + THICKNESS
def Y2 = MAX_BOUND - THICKNESS; def Y3 = MAX_BOUND

switch (shapeType) {
    case 'q': vertices = [[X0, Y0], [X3, Y0], [X3, Y3], [X0, Y3]]; break
    case 'l': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y3], [X0, Y3]]; break
    case 'c': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y2], [X3, Y2], [X3, Y3], [X0, Y3]]; break
    case 'h':
        def Y_MID_START = 50000 - (THICKNESS / 2).toInteger()
        def Y_MID_END = 50000 + (THICKNESS / 2).toInteger()
        vertices = [[X0, Y0], [X1, Y0], [X1, Y_MID_START], [X2, Y_MID_START], [X2, Y0], [X3, Y0],
                    [X3, Y3], [X2, Y3], [X2, Y_MID_END], [X1, Y_MID_END], [X1, Y3], [X0, Y3]]; break
}

// ============================================================
// HELPERS
// ============================================================
def rnd = new Random(RANDOM_SEED)
def spatialIndex = new SpatialIndex()

def linesIntersect = { p1, p2, p3, p4 ->
    return Line2D.linesIntersect(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1])
}

// --- GEOMETRY GENERATORS ---

def makeSteps = { pts ->
    def res = []
    for (int i=0; i<pts.size()-1; i++) {
        def p1 = pts[i]; def p2 = pts[i+1]
        res << p1
        if (p1[0] != p2[0] && p1[1] != p2[1]) {
            res << [p1[0], p2[1]]
        }
    }
    res << pts[-1]
    return res
}

def generateLinearStairs = { start, end, steps ->
    if (steps <= 0) return makeSteps([start, end])
    def targets = []
    long dx = end[0] - start[0]; long dy = end[1] - start[1]
    for (int i = 0; i <= steps+1; i++) {
        double t = i / (double)(steps+1)
        targets << [start[0] + dx*t, start[1] + dy*t]
    }
    return makeSteps(targets)
}

def generateRoundStairs = { start, end, steps, isHoriz ->
    if (steps <= 0) return makeSteps([start, end])
    def targets = []
    long dx_total = end[0] - start[0]; long dy_total = end[1] - start[1]

    for (int i = 0; i <= steps+1; i++) {
        double t = i / (double)(steps+1)
        double angle = t * (Math.PI / 2.0)
        double dx = Math.sin(angle)
        double dy = 1.0 - Math.cos(angle)

        long tx, ty
        if (isHoriz) {
            tx = start[0] + (long)(dx_total * dx)
            ty = start[1] + (long)(dy_total * dy)
        } else {
            tx = start[0] + (long)(dx_total * dy)
            ty = start[1] + (long)(dy_total * dx)
        }
        targets << [tx, ty]
    }
    return makeSteps(targets)
}

def selectWeightedEdge = { verts ->
    def n = verts.size(); def lengths = new double[n]; double totalLen = 0
    for (int i = 0; i < n; i++) {
        def p1 = verts[i]; def p2 = verts[(i + 1) % n]
        double len = (p1[0] - p2[0]).abs() + (p1[1] - p2[1]).abs()
        lengths[i] = len; totalLen += len
    }
    double r = rnd.nextDouble() * totalLen
    double acc = 0
    for (int i = 0; i < n; i++) {
        acc += lengths[i]; if (r <= acc) return [i, lengths[i]]
    }
    return [n-1, lengths[n-1]]
}

// ------------------------------------------------------------
// VALIDATION
// ------------------------------------------------------------

def checkIntersection = { newPoints, allVertices, changedEdgeIdx, candidates ->
    def newEdges = []; for (int i = 0; i < newPoints.size() - 1; i++) newEdges << [newPoints[i], newPoints[i+1]]
    int n = allVertices.size()
    for (int i : candidates) {
        if (i == changedEdgeIdx) continue
        def v1 = allVertices[i]; def v2 = allVertices[(i + 1) % n]
        for (def newEdge : newEdges) {
            if (linesIntersect(newEdge[0], newEdge[1], v1, v2)) {
                if (newEdge[0] == v1 || newEdge[0] == v2 || newEdge[1] == v1 || newEdge[1] == v2) continue
                return false
            }
            if (Line2D.ptSegDistSq(v1[0], v1[1], v2[0], v2[1], newEdge[0][0], newEdge[0][1]) == 0 ||
                    Line2D.ptSegDistSq(v1[0], v1[1], v2[0], v2[1], newEdge[1][0], newEdge[1][1]) == 0) return false
        }
    }
    return true
}

def checkProximity = { newPoints, allVertices, changedEdgeIdx, minGap, candidates ->
    int n = allVertices.size()
    for (int k = 1; k < newPoints.size() - 1; k++) {
        def np = newPoints[k]
        for (int i : candidates) {
            if (i == changedEdgeIdx) continue
            def v1 = allVertices[i]; def v2 = allVertices[(i + 1) % n]
            if (Line2D.ptSegDist(v1[0], v1[1], v2[0], v2[1], np[0], np[1]) < minGap) return false
        }
    }
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
// MAIN LOOP
// ============================================================
spatialIndex.rebuild(vertices)

phases.each { phase ->
    System.err.println "Starting Phase: ${phase.name} (Vertices: ${vertices.size()})"
    int modificationsMade = 0
    int totalFails = 0
    int maxTotalFails = (phase.name == "Micro") ? 5000 : 1500

    while (totalFails < maxTotalFails) {
        if (phase.count != -1 && modificationsMade >= phase.count) break
        if (vertices.size() >= TARGET_VERTICES) break

        if (vertices.size() % 100 == 0) {
            int pct = ((vertices.size() / (double)TARGET_VERTICES) * 100).toInteger()
            System.err.print("\rVertices: ${vertices.size()}/${TARGET_VERTICES} ($pct%) ")
        }

        def selection = selectWeightedEdge(vertices)
        int idx = selection[0]; double edgeLen = selection[1]

        if (edgeLen < phase.minLen * 1.5) { totalFails++; continue }

        boolean successOnEdge = false
        int edgeRetries = 0

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

            if (checkIntersection(bbox, vertices, idx, candidates) &&
                    checkProximity(bbox, vertices, idx, phase.minGap, candidates)) {

                def pointsToAdd = []
                if (type == "box") {
                    pointsToAdd = [n1, rect_n2, rect_n3, n4]
                } else {
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

                vertices.addAll(idx + 1, pointsToAdd)
                spatialIndex.rebuild(vertices)
                modificationsMade++
                successOnEdge = true
                totalFails = 0
            }
        }
        if (!successOnEdge) totalFails++
    }
    System.err.println "\nPhase ${phase.name} complete."
}

System.err.println "Finished! Final count: ${vertices.size()}"
System.err.println "Saving to ${csvFile.name}..."
csvFile.withWriter { w -> vertices.each { w.writeLine("${it[0]},${it[1]}") } }

System.err.println "Generating PNG..."
try {
    def img = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB)
    def g = img.createGraphics(); g.setColor(Color.BLACK); g.fillRect(0,0,IMAGE_SIZE,IMAGE_SIZE)
    def poly = new java.awt.Polygon()
    double sc = IMAGE_SIZE / CANVAS_SIZE
    vertices.each { poly.addPoint((int)(it[0]*sc), (int)(it[1]*sc)) }
    g.setColor(Color.RED); g.fillPolygon(poly); g.dispose()
    ImageIO.write(img, "PNG", imgFile)
    System.err.println "Done."
} catch (e) { e.printStackTrace() }