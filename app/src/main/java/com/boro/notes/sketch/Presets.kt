package com.boro.notes.sketch

/** Starting garments to customise. One grid square is about an inch. */
enum class Preset(val label: String) {
    BLANK("Blank canvas"),
    JEANS_FRONT("Jeans (front)"),
    JEANS_BACK("Jeans (back)"),
    TRUCKER_JACKET("Trucker jacket (front)"),
    SHIRT("Shirt (front)");

    fun shapes(): List<Shape> = when (this) {
        BLANK -> emptyList()
        JEANS_FRONT -> PresetShapes.jeansFront()
        JEANS_BACK -> PresetShapes.jeansBack()
        TRUCKER_JACKET -> PresetShapes.truckerJacket()
        SHIRT -> PresetShapes.shirt()
    }
}

private object PresetShapes {
    private fun p(x: Number, y: Number) = Pt(x.toFloat(), y.toFloat())

    /** A shape through [pts]; points whose index is in [smooth] are curve points. */
    private fun shape(vararg pts: Pt, closed: Boolean = false, smooth: Set<Int> = emptySet(), stitch: Boolean = false, group: Long? = null) =
        Shape(
            points = pts.toList(),
            corners = pts.indices.map { it !in smooth },
            closed = closed,
            style = if (stitch) LineStyle.STITCH else LineStyle.SOLID,
            group = group,
        )

    private fun line(a: Pt, b: Pt, stitch: Boolean = false, group: Long? = null) = shape(a, b, stitch = stitch, group = group)

    private fun box(a: Pt, b: Pt, group: Long? = null) =
        shape(a, p(b.x, a.y), b, p(a.x, b.y), closed = true, group = group)

    private fun stamp(at: Pt, stamp: Stamp, group: Long? = null) =
        Shape(listOf(at), listOf(true), stamp = stamp, group = group)

    /** A pocket: its outline, topstitching inside, and optional rivets, grouped as one piece. */
    private fun pocket(outline: Shape, group: Long, rivets: List<Pt> = emptyList()): List<Shape> {
        val base = outline.copy(group = group)
        return listOfNotNull(base, Edits.topstitch(base)) + rivets.map { stamp(it, Stamp.RIVET, group) }
    }

    private fun beltLoops(xs: List<Number>) = xs.map { x ->
        val fx = x.toFloat()
        box(p(fx - 0.3f, -0.3f), p(fx + 0.3f, 1.8f))
    }

    private fun jeansOutline() = shape(
        p(2, 0), p(20, 0), p(21, 9), p(20, 42), p(12, 42), p(11, 14), p(10, 42), p(2, 42), p(1, 9),
        closed = true,
    )

    private fun hems() = listOf(
        line(p(2, 41), p(10, 41), stitch = true),
        line(p(12, 41), p(20, 41), stitch = true),
    )

    fun jeansFront(): List<Shape> = buildList {
        add(jeansOutline())
        add(line(p(2, 1.5), p(20, 1.5)))
        addAll(beltLoops(listOf(4, 8, 14, 18)))
        add(stamp(p(11.5, 0.75), Stamp.BUTTON))
        // Fly J-stitch and centre seam.
        add(shape(p(12.5, 1.5), p(12.5, 8.5), p(12.1, 10.3), p(11, 11), smooth = setOf(2), stitch = true))
        add(line(p(11, 1.5), p(11, 14)))
        // Scooped front pockets with rivets at the corners.
        add(shape(p(6, 1.5), p(5.2, 4.6), p(1.6, 6), smooth = setOf(1), group = 1))
        add(stamp(p(1.9, 5.8), Stamp.RIVET, 1))
        add(stamp(p(5.9, 1.9), Stamp.RIVET, 1))
        add(shape(p(16, 1.5), p(16.8, 4.6), p(20.4, 6), smooth = setOf(1), group = 2))
        add(stamp(p(20.1, 5.8), Stamp.RIVET, 2))
        add(stamp(p(16.1, 1.9), Stamp.RIVET, 2))
        // Coin pocket.
        addAll(pocket(box(p(16.5, 2.2), p(19, 4.4)), group = 3))
        addAll(hems())
    }

    fun jeansBack(): List<Shape> = buildList {
        add(jeansOutline())
        add(line(p(2, 1.5), p(20, 1.5)))
        addAll(beltLoops(listOf(3.5, 8, 11, 14, 18.5)))
        // Pointed yoke and centre seam.
        add(shape(p(1.3, 6), p(11, 4), p(20.7, 6)))
        add(line(p(11, 4), p(11, 14)))
        // Five-sided back pockets with topstitching.
        addAll(pocket(shape(p(3.5, 7), p(9, 7), p(9, 12), p(6.25, 13.3), p(3.5, 12), closed = true), group = 1))
        addAll(pocket(shape(p(13, 7), p(18.5, 7), p(18.5, 12), p(15.75, 13.3), p(13, 12), closed = true), group = 2))
        addAll(hems())
    }

    fun truckerJacket(): List<Shape> = buildList {
        // Body with a curved neckline.
        add(shape(p(6, 0), p(1, 2), p(1, 22), p(21, 22), p(21, 2), p(16, 0), p(11, 2.5), closed = true, smooth = setOf(6)))
        add(line(p(11, 2.5), p(11, 22)))
        add(line(p(12, 2.5), p(12, 19.5), stitch = true))
        // Yoke, waistband and pleat stitching.
        add(line(p(1, 6), p(10.5, 6)))
        add(line(p(11.5, 6), p(21, 6)))
        add(line(p(1, 19.5), p(21, 19.5)))
        add(line(p(5, 10.5), p(5, 19.5), stitch = true))
        add(line(p(17, 10.5), p(17, 19.5), stitch = true))
        // Chest pocket flaps with buttons.
        addAll(pocket(shape(p(3.5, 7), p(8.5, 7), p(8.5, 9), p(6, 10.2), p(3.5, 9), closed = true), group = 1))
        add(stamp(p(6, 9.2), Stamp.BUTTON, 1))
        addAll(pocket(shape(p(13.5, 7), p(18.5, 7), p(18.5, 9), p(16, 10.2), p(13.5, 9), closed = true), group = 2))
        add(stamp(p(16, 9.2), Stamp.BUTTON, 2))
        listOf(4, 8, 12, 16, 20.7).forEach { y -> add(stamp(p(11.5, y), Stamp.BUTTON)) }
    }

    fun shirt(): List<Shape> = buildList {
        // Body with a curved shirt-tail hem.
        add(
            shape(
                p(7, 0), p(2, 2), p(2, 24), p(6, 26.5), p(11, 25.8), p(16, 26.5), p(20, 24), p(20, 2), p(15, 0), p(11, 2),
                closed = true,
                smooth = setOf(3, 4, 5, 9),
            )
        )
        // Collar points and button placket.
        add(shape(p(7, 0), p(11, 2), p(8.5, 3.6), closed = true))
        add(shape(p(15, 0), p(11, 2), p(13.5, 3.6), closed = true))
        add(line(p(10.3, 3), p(10.3, 25.8), stitch = true))
        add(line(p(11.7, 3), p(11.7, 25.8), stitch = true))
        add(line(p(2, 4.5), p(10, 4.5), stitch = true))
        add(line(p(12, 4.5), p(20, 4.5), stitch = true))
        // Chest pocket with clipped bottom corners.
        val chest = Edits.corners(box(p(4, 7), p(8, 11.5)), Edits.CornerStyle.CLIPPED, 0.6f)
        addAll(pocket(chest, group = 1))
        listOf(5, 9, 13, 17, 21).forEach { y -> add(stamp(p(11, y), Stamp.BUTTON)) }
    }
}
