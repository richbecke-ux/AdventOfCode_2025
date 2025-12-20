package aoc2025.day9

import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.awt.*
import java.util.List

// ============================================================
// 1. ART CONFIGURATION
// ============================================================
def TARGET_VERTICES = 2500
def CANVAS_SIZE = 100000
def IMAGE_SIZE = 4096
def PADDING = 15000
def SEED = System.currentTimeMillis()

// Palettes: [Background, MainFill, Glow, Accent]
def PALETTES = [
        "Cyberpunk": [new Color(10, 10, 16), new Color(0, 255, 255, 40), new Color(255, 0, 128), new Color(0, 255, 255)],
        "Blueprint": [new Color(20, 35, 60), new Color(30, 60, 100, 100), new Color(200, 220, 255), new Color(255, 255, 255)],
        "Matrix":    [new Color(0, 10, 0),    new Color(0, 50, 0, 80),    new Color(0, 255, 50),   new Color(200, 255, 200)],
        "Mars":      [new Color(30, 10, 10),  new Color(100, 40, 20, 60), new Color(255, 80, 40),  new Color(255, 200, 100)],
        "Void":      [new Color(5, 5, 5),     new Color(20, 20, 20, 255), new Color(255, 255, 255), new Color(100, 100, 100)]
]

// ============================================================
// 2. GEOMETRY ENGINE
// ============================================================
def rnd = new Random(SEED)

def shapes = ['q', 'l', 'c', 'h', 't', 'x', 's']
def shapeType = shapes[rnd.nextInt(shapes.size())]
if (args.any { it.startsWith('-') }) shapeType = args.find{it.startsWith('-')}.substring(1)

def paletteName = PALETTES.keySet().toList()[rnd.nextInt(PALETTES.size())]
def colors = PALETTES[paletteName]
def BG_COL=colors[0]; def FILL_COL=colors[1]; def GLOW_COL=colors[2]; def ACCENT_COL=colors[3]

System.err.println "Generating Art..."
System.err.println "Shape: ${shapeType.toUpperCase()} | Theme: ${paletteName} | Seed: ${SEED}"

// --- Shape Definitions ---
def vertices = []
def MIN=30000; def MAX=70000; def THICK=12000
def X0=MIN; def X1=MIN+THICK; def X2=MAX-THICK; def X3=MAX
def Y0=MIN; def Y1=MIN+THICK; def Y2=MAX-THICK; def Y3=MAX
def XC1=50000-(THICK/2).toInteger(); def XC2=50000+(THICK/2).toInteger()
def YC1=50000-(THICK/2).toInteger(); def YC2=50000+(THICK/2).toInteger()

switch (shapeType) {
    case 'q': vertices = [[X0, Y0], [X3, Y0], [X3, Y3], [X0, Y3]]; break
    case 'l': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y3], [X0, Y3]]; break
    case 'c': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [X1, Y1], [X1, Y2], [X3, Y2], [X3, Y3], [X0, Y3]]; break
    case 'h': vertices = [[X0, Y0], [X1, Y0], [X1, YC1], [X2, YC1], [X2, Y0], [X3, Y0], [X3, Y3], [X2, Y3], [X2, YC2], [X1, YC2], [X1, Y3], [X0, Y3]]; break
    case 't': vertices = [[X0, Y0], [X3, Y0], [X3, Y1], [XC2, Y1], [XC2, Y3], [XC1, Y3], [XC1, Y1], [X0, Y1]]; break
    case 'x': vertices = [[XC1, Y0], [XC2, Y0], [XC2, YC1], [X3, YC1], [X3, YC2], [XC2, YC2], [XC2, Y3], [XC1, Y3], [XC1, YC2], [X0, YC2], [X0, YC1], [XC1, YC1]]; break
    case 's': vertices = [[X1, Y0], [X3, Y0], [X3, YC2], [XC2, YC2], [XC2, Y3], [X0, Y3], [X0, YC1], [X1, YC1]]; break
}

// --- Helpers ---
class SpatialIndex {
    int sz = 5000; Map<String, List> grid = [:]
    def rebuild(verts) { grid.clear(); int n=verts.size(); for(int i=0;i<n;i++) {
        def v1=verts[i]; def v2=verts[(i+1)%n];
        int xA=(Math.min(v1[0],v2[0])/sz) as int; int xB=(Math.max(v1[0],v2[0])/sz) as int
        int yA=(Math.min(v1[1],v2[1])/sz) as int; int yB=(Math.max(v1[1],v2[1])/sz) as int
        for(x in xA..xB) for(y in yA..yB) { String k="$x,$y"; if(!grid[k]) grid[k]=[]; grid[k]<<i }
    }}
    def get(x1,y1,x2,y2,pad) { Set c=new HashSet(); int xA=((Math.min(x1,x2)-pad)/sz) as int; int xB=((Math.max(x1,x2)+pad)/sz) as int
        int yA=((Math.min(y1,y2)-pad)/sz) as int; int yB=((Math.max(y1,y2)+pad)/sz) as int
        for(x in xA..xB) for(y in yA..yB) { String k="$x,$y"; if(grid[k]) c.addAll(grid[k]) }; return c
    }
}
def si = new SpatialIndex(); si.rebuild(vertices)

def linesIntersect = { p1, p2, p3, p4 -> Line2D.linesIntersect(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1]) }

def check = { pts, verts, idx, gap, cands ->
    def edges=[]; for(int i=0; i<pts.size()-1; i++) edges<<[pts[i], pts[i+1]]
    int n=verts.size()
    for(int i : cands) { if(i==idx) continue; def v1=verts[i]; def v2=verts[(i+1)%n]
        for(e in edges) if(linesIntersect(e[0],e[1],v1,v2)) {
            if(!(e[0]==v1||e[0]==v2||e[1]==v1||e[1]==v2)) return false
        }
    }
    for(int k=1; k<pts.size()-1; k++) for(int i : cands) {
        if(i==idx) continue; def v1=verts[i]; def v2=verts[(i+1)%n]
        if(Line2D.ptSegDist(v1[0],v1[1],v2[0],v2[1],pts[k][0],pts[k][1]) < gap) return false
    }
    for(int i : cands) {
        if(i==idx||i==(idx+1)%n) continue; def v=verts[i]
        for(e in edges) if(Line2D.ptSegDist(e[0][0],e[0][1],e[1][0],e[1][1],v[0],v[1]) < gap) return false
    }
    return true
}

def generateComplexStairs = { a, b, s, h, isRound ->
    def res = []
    long dx = b[0] - a[0]
    long dy = b[1] - a[1]

    for (int k = 0; k <= s + 1; k++) {
        double t = k / (double)(s + 1)
        double xx, yy
        if (isRound) {
            double ang = t * (Math.PI / 2.0)
            if (h) { xx = a[0] + dx * Math.sin(ang); yy = a[1] + dy * (1.0 - Math.cos(ang)) }
            else { xx = a[0] + dx * (1.0 - Math.cos(ang)); yy = a[1] + dy * Math.sin(ang) }
        } else {
            xx = a[0] + dx * t; yy = a[1] + dy * t
        }
        if (res.size() > 0) {
            long prevX = res[-1][0]; long prevY = res[-1][1]
            if (prevX != (long)xx && prevY != (long)yy) res << [prevX, (long)yy]
        }
        res << [(long)xx, (long)yy]
    }
    return res
}

// --- Generation Loop ---
def phases = [
        [n:"Macro", cnt:8, minL:8000, maxL:20000, minD:3000, maxD:8000, s:5, g:4000, t:["pyramid","round","box"]],
        [n:"Meso", cnt:50, minL:2500, maxL:7000, minD:1500, maxD:4000, s:3, g:1500, t:["box","pyramid"]],
        [n:"Micro", cnt:-1, minL:400, maxL:1500, minD:200, maxD:1000, s:0, g:400, t:["box","box","box","pyramid"]]
]

phases.each { p ->
    System.err.println "Starting Phase: ${p.n} (Current Vertices: ${vertices.size()})"
    int fails=0; int mods=0;
    // OPTIMALISERING: Redusert maks antall feil før den gir opp fasen
    int maxF = (p.n=="Micro")?2000:500

    while(fails < maxF && (p.cnt==-1 || mods<p.cnt) && vertices.size()<TARGET_VERTICES) {

        int n=vertices.size(); double sum=0; double[] w=new double[n];
        for(int i=0;i<n;i++) { double len = (vertices[i][0]-vertices[(i+1)%n][0]).abs() + (vertices[i][1]-vertices[(i+1)%n][1]).abs(); w[i]=len; sum+=len }
        double r=rnd.nextDouble()*sum; double acc=0; int idx=0
        for(int i=0;i<n;i++) { acc+=w[i]; if(r<=acc) { idx=i; break } }

        double elen=w[idx]; if(elen < p.minL*1.2) { fails++; continue }
        long slen = rnd.nextInt((int)(p.maxL-p.minL))+p.minL; if(slen>=elen) slen=(long)(elen*0.7)
        long off = rnd.nextInt((int)(elen-slen)); long d = rnd.nextInt((int)(p.maxD-p.minD))+p.minD
        int dir = rnd.nextBoolean()?1:-1; String type = p.t[rnd.nextInt(p.t.size())]

        def v1=vertices[idx]; def v2=vertices[(idx+1)%n]; boolean hor=(v1[1]==v2[1])

        def n1, n4, r2, r3
        if(hor) { long y=v1[1]; long xb=Math.min(v1[0],v2[0]); n1=[xb+off,y]; n4=[xb+off+slen,y]; r2=[n1[0],y+d*dir]; r3=[n4[0],y+d*dir] }
        else { long x=v1[0]; long yb=Math.min(v1[1],v2[1]); n1=[x,yb+off]; n4=[x,yb+off+slen]; r2=[x+d*dir,n1[1]]; r3=[x+d*dir,n4[1]] }

        long mx1=Math.min(n1[0],r2[0])-p.g; long mx2=Math.max(n1[0],r2[0])+p.g
        long my1=Math.min(n1[1],r2[1])-p.g; long my2=Math.max(n1[1],r2[1])+p.g
        def cands = si.get(mx1,my1,mx2,my2,p.g)

        if(check([n1,r2,r3,n4], vertices, idx, p.g, cands)) {
            def pts=[]
            if(type=="box" || p.s==0) pts=[n1,r2,r3,n4]
            else {
                double shr=0.25; long sa=(long)(slen*shr)
                def ts, te; if(hor) { ts=[r2[0]+sa,r2[1]]; te=[r3[0]-sa,r3[1]] } else { ts=[r2[0],r2[1]+sa]; te=[r3[0],r3[1]-sa] }
                pts.addAll(generateComplexStairs(n1, ts, p.s, hor, type=="round"))
                pts.addAll(generateComplexStairs(te, n4, p.s, hor, type=="round"))
            }

            boolean rev = false; if(hor && v1[0]>v2[0]) rev=true; if(!hor && v1[1]>v2[1]) rev=true
            if(rev) pts=pts.reverse()
            vertices.addAll(idx+1, pts.unique())
            si.rebuild(vertices); mods++; fails=0

            // PROGRESS BAR
            if (vertices.size() % 50 == 0) {
                int pct = ((vertices.size() / (double)TARGET_VERTICES) * 100).toInteger()
                System.err.print("\rProgress: ${vertices.size()}/${TARGET_VERTICES} ($pct%) - Phase: ${p.n} ")
            }

        } else fails++
    }
    System.err.println "\nPhase ${p.n} complete."
}

// ============================================================
// 3. RENDER ENGINE
// ============================================================
System.err.println "Rendering image..."
def img = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB)
def g2d = img.createGraphics()
g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

def center = new Point2D.Float(IMAGE_SIZE/2, IMAGE_SIZE/2)
float radius = IMAGE_SIZE * 0.8f
float[] dist = [0.0f, 1.0f]
Color[] colorsArr = [BG_COL.brighter(), BG_COL]
RadialGradientPaint p = new RadialGradientPaint(center, radius, dist, colorsArr)
g2d.setPaint(p)
g2d.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE)

g2d.setColor(new Color(ACCENT_COL.getRed(), ACCENT_COL.getGreen(), ACCENT_COL.getBlue(), 20))
int gridSize = 100
for(int i=0; i<IMAGE_SIZE; i+=gridSize) {
    g2d.drawLine(0, i, IMAGE_SIZE, i)
    g2d.drawLine(i, 0, i, IMAGE_SIZE)
}

double sc = (IMAGE_SIZE - 200) / (double)CANVAS_SIZE
Path2D poly = new Path2D.Double()
vertices.eachWithIndex { v, i ->
    double x = v[0] * sc + 100
    double y = v[1] * sc + 100
    if(i==0) poly.moveTo(x, y) else poly.lineTo(x, y)
}
poly.closePath()

g2d.setColor(FILL_COL)
g2d.fill(poly)

g2d.setClip(poly)
g2d.setColor(new Color(GLOW_COL.getRed(), GLOW_COL.getGreen(), GLOW_COL.getBlue(), 40))
for(int i=0; i<IMAGE_SIZE; i+=20) {
    g2d.drawLine(0, i, IMAGE_SIZE, i)
}
g2d.setClip(null)

g2d.setStroke(new BasicStroke(4.0f))
g2d.setColor(new Color(GLOW_COL.getRed(), GLOW_COL.getGreen(), GLOW_COL.getBlue(), 60))
g2d.draw(poly)
g2d.setStroke(new BasicStroke(1.5f))
g2d.setColor(GLOW_COL)
g2d.draw(poly)

g2d.setColor(ACCENT_COL)
g2d.setFont(new Font("Monospaced", Font.BOLD, 40))
g2d.drawString("FIG 9-A: ${shapeType.toUpperCase()}-CLASS ANOMALY", 100, 80)
g2d.setFont(new Font("Monospaced", Font.PLAIN, 20))
g2d.drawString("VERTICES: ${vertices.size()}", 100, 120)
g2d.drawString("SEED: ${SEED}", 100, 150)
g2d.drawString("SYS.PALETTE: ${paletteName.toUpperCase()}", 100, 180)

int cLen = 200
g2d.setStroke(new BasicStroke(8.0f))
g2d.drawLine(50, 50, 50+cLen, 50)
g2d.drawLine(50, 50, 50, 50+cLen)

g2d.drawLine(IMAGE_SIZE-50, 50, IMAGE_SIZE-50-cLen, 50)
g2d.drawLine(IMAGE_SIZE-50, 50, IMAGE_SIZE-50, 50+cLen)

g2d.drawLine(50, IMAGE_SIZE-50, 50+cLen, IMAGE_SIZE-50)
g2d.drawLine(50, IMAGE_SIZE-50, 50, IMAGE_SIZE-50-cLen)

g2d.drawLine(IMAGE_SIZE-50, IMAGE_SIZE-50, IMAGE_SIZE-50-cLen, IMAGE_SIZE-50)
g2d.drawLine(IMAGE_SIZE-50, IMAGE_SIZE-50, IMAGE_SIZE-50, IMAGE_SIZE-50-cLen)

g2d.dispose()

def fname = "art_${shapeType}_${paletteName}_${System.currentTimeMillis()}.png"
ImageIO.write(img, "PNG", new File(fname))
System.err.println "Saved art to: $fname"