package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * The platform's logo mark (a «T» with its top-right corner folded, and a leaf) drawn as vector paths in a
 * PDF content stream, so it stays sharp at any zoom and in print, with no image resource and no new
 * dependency. The geometry is the one of frontend/public/brand/logo-mono.svg (white on the brand blue):
 * the mark without its rounded tile, because the header band already is the blue.
 *
 * The leaf is cut out of the «T» by a halo in the band's colour, as in the SVG (where a mask does it).
 */
final class CertificateLogo {

    /** The mark's extent in the SVG's 512-unit space: x 118.3..392.5, y 111.9..400. */
    private static final float LEFT = 118.3f;
    private static final float TOP = 111.9f;
    private static final float HEIGHT_UNITS = 400f - TOP;
    private static final float WIDTH_UNITS = 392.5f - LEFT;
    /** The halo around the leaf, in SVG units (the SVG mask strokes 16 units: 8 on each side). */
    private static final float HALO_UNITS = 16f;
    private static final float VEIN_UNITS = 5f;

    private CertificateLogo() {
    }

    /** Width of the mark when drawn {@code height} points tall. */
    static float widthFor(float height) {
        return WIDTH_UNITS / HEIGHT_UNITS * height;
    }

    /**
     * Draws the mark with its top-left corner at ({@code x}, {@code top}) and the given height, filled with
     * {@code ink}; {@code background} is the colour behind it (it paints the halo and the vein).
     */
    static void draw(PDPageContentStream cs, float x, float top, float height, float[] ink, float[] background)
            throws IOException {
        float s = height / HEIGHT_UNITS;
        Mapper m = new Mapper(x, top, s);
        cs.saveGraphicsState();

        // The «T», without the leaf's bite: the bite is the halo painted over it below.
        cs.setNonStrokingColor(ink[0], ink[1], ink[2]);
        cs.moveTo(m.x(118.3f), m.y(111.9f));
        cs.lineTo(m.x(329.8f), m.y(111.9f));
        cs.lineTo(m.x(297.6f), m.y(186.4f));
        cs.lineTo(m.x(297.6f), m.y(399.7f));
        cs.lineTo(m.x(216.2f), m.y(399.7f));
        cs.lineTo(m.x(216.2f), m.y(191.1f));
        cs.lineTo(m.x(118.3f), m.y(191.1f));
        cs.closePath();
        cs.fill();

        // The folded corner: the flap, and the sliver of the fold's edge under it.
        cs.moveTo(m.x(331.6f), m.y(111.9f));
        cs.lineTo(m.x(392.5f), m.y(178.2f));
        cs.lineTo(m.x(331.6f), m.y(178.2f));
        cs.closePath();
        cs.fill();
        cs.moveTo(m.x(338.4f), m.y(190.4f));
        cs.lineTo(m.x(392.5f), m.y(180f));
        cs.lineTo(m.x(392.5f), m.y(189.3f));
        cs.closePath();
        cs.fill();

        // The leaf's halo (the background colour, stroked around it), then the leaf itself.
        cs.setStrokingColor(background[0], background[1], background[2]);
        cs.setLineWidth(HALO_UNITS * s);
        cs.setLineJoinStyle(1);
        leafPath(cs, m);
        cs.closeAndStroke();
        cs.setNonStrokingColor(ink[0], ink[1], ink[2]);
        leafPath(cs, m);
        cs.closePath();
        cs.fill();

        // The leaf's vein, in the background colour.
        cs.setStrokingColor(background[0], background[1], background[2]);
        cs.setLineWidth(VEIN_UNITS * s);
        cs.setLineCapStyle(1);
        cs.moveTo(m.x(349.5f), m.y(335.2f));
        cs.curveTo(m.x(330f), m.y(358f), m.x(312f), m.y(372f), m.x(297.5f), m.y(385.4f));
        cs.stroke();

        cs.restoreGraphicsState();
    }

    private static void leafPath(PDPageContentStream cs, Mapper m) throws IOException {
        cs.moveTo(m.x(285f), m.y(396f));
        cs.curveTo(m.x(272f), m.y(345f), m.x(320f), m.y(300f), m.x(391f), m.y(294f));
        cs.curveTo(m.x(392f), m.y(345f), m.x(370f), m.y(395f), m.x(300f), m.y(400f));
        cs.curveTo(m.x(295f), m.y(400f), m.x(289f), m.y(398f), m.x(285f), m.y(396f));
    }

    /** SVG space (y down) to PDF space (y up), for a mark whose top-left corner is at (x0, top). */
    private record Mapper(float x0, float top, float scale) {
        float x(float svgX) {
            return x0 + (svgX - LEFT) * scale;
        }

        float y(float svgY) {
            return top - (svgY - TOP) * scale;
        }
    }
}
