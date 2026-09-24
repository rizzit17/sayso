package com.samsung.prism.teachable.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiObservationTest {

    @Test
    fun testBoundsCalculationsAndJson() {
        val bounds = Bounds(left = 100, top = 200, right = 400, bottom = 600)
        assertEquals(300, bounds.width)
        assertEquals(400, bounds.height)
        assertEquals(250, bounds.centerX)
        assertEquals(400, bounds.centerY)
        assertTrue(bounds.contains(250, 400))
        assertFalse(bounds.contains(50, 50))

        val (normX, normY) = bounds.normalizedCenter(1000, 2000)
        assertEquals(0.25f, normX, 0.001f)
        assertEquals(0.20f, normY, 0.001f)

        val json = bounds.toJson()
        val restored = Bounds.fromJson(json)
        assertEquals(bounds, restored)
    }

    @Test
    fun testUiNodeRoleInferenceAndDescriptor() {
        val buttonNode = UiNode(
            resourceId = "com.application.zomato:id/btn_add_to_cart",
            text = "Add to Cart",
            className = "android.widget.Button",
            clickable = true,
            bounds = Bounds(100, 500, 400, 600)
        )
        val role = UiTreeCapture.inferSemanticRole(
            className = buttonNode.className,
            isClickable = buttonNode.clickable,
            isScrollable = false,
            text = buttonNode.text,
            contentDescription = null,
            resourceId = buttonNode.resourceId
        )
        assertEquals("button", role)

        val nodeWithRole = buttonNode.copy(semanticRole = role)
        val desc = nodeWithRole.toDescriptor()
        assertTrue(desc.contains("[button]"))
        assertTrue(desc.contains("'Add to Cart'"))
        assertTrue(desc.contains("id=btn_add_to_cart"))
        assertTrue(desc.contains("[clickable]"))
    }

    @Test
    fun testUiNodeTreeFlattenAndJsonSerialization() {
        val child1 = UiNode(
            resourceId = "com.application.zomato:id/tv_title",
            text = "Margherita Pizza",
            className = "android.widget.TextView",
            semanticRole = "text_label",
            bounds = Bounds(120, 210, 380, 260)
        )
        val child2 = UiNode(
            resourceId = "com.application.zomato:id/btn_add",
            text = "ADD",
            className = "android.widget.Button",
            clickable = true,
            semanticRole = "button",
            bounds = Bounds(390, 210, 450, 260)
        )
        val parent = UiNode(
            resourceId = "com.application.zomato:id/item_card",
            className = "android.widget.FrameLayout",
            semanticRole = "list_item",
            clickable = true,
            bounds = Bounds(100, 200, 460, 270),
            children = listOf(child1, child2)
        )

        val flat = parent.flatten()
        assertEquals(3, flat.size)
        assertEquals(parent, flat[0])
        assertEquals(child1, flat[1])
        assertEquals(child2, flat[2])

        val json = parent.toJson()
        val restored = UiNode.fromJson(json)
        assertEquals(parent.resourceId, restored.resourceId)
        assertEquals(parent.children.size, restored.children.size)
        assertEquals(child1.text, restored.children[0].text)
        assertEquals(child2.clickable, restored.children[1].clickable)
    }

    @Test
    fun testUiSnapshotSearchAndSignature() {
        val searchBox = UiNode(
            resourceId = "com.application.zomato:id/search_bar",
            text = "Search for restaurant or dish",
            className = "android.widget.EditText",
            clickable = true,
            semanticRole = "search_box",
            bounds = Bounds(50, 100, 500, 180)
        )
        val root = UiNode(
            className = "android.widget.LinearLayout",
            bounds = Bounds(0, 0, 1080, 2400),
            children = listOf(searchBox)
        )
        val snapshot = UiSnapshot(
            packageName = "com.application.zomato",
            rootNode = root
        )

        assertEquals(2, snapshot.nodeCount)
        assertFalse(snapshot.screenSignature.isBlank())

        val found = snapshot.findNodesByText("search")
        assertEquals(1, found.size)
        assertEquals(searchBox.resourceId, found[0].resourceId)

        val roleMatches = snapshot.findNodesByRole("search_box")
        assertEquals(1, roleMatches.size)

        val json = snapshot.toJson()
        val restored = UiSnapshot.fromJson(json)
        assertEquals(snapshot.packageName, restored.packageName)
        assertEquals(snapshot.nodeCount, restored.nodeCount)
    }

    @Test
    fun testUiDiffStateTransitions() {
        val node1 = UiNode(
            resourceId = "com.application.zomato:id/tv_qty",
            text = "Qty: 1",
            className = "android.widget.TextView",
            bounds = Bounds(100, 100, 200, 150)
        )
        val snapshot1 = UiSnapshot(
            packageName = "com.application.zomato",
            rootNode = UiNode(children = listOf(node1))
        )

        // Same snapshot should produce empty diff
        val emptyDiff = UiDiff.compute(snapshot1, snapshot1)
        assertTrue(emptyDiff.isEmpty())
        assertFalse(emptyDiff.significantChange)

        // Quantity changed from 1 to 2
        val node1Updated = node1.copy(text = "Qty: 2")
        val snapshot2 = UiSnapshot(
            packageName = "com.application.zomato",
            rootNode = UiNode(children = listOf(node1Updated))
        )
        val diffQty = UiDiff.compute(snapshot1, snapshot2)
        assertEquals(1, diffQty.textChangedNodes.size)
        assertEquals("Qty: 1", diffQty.textChangedNodes[0].first.text)
        assertEquals("Qty: 2", diffQty.textChangedNodes[0].second.text)
        assertTrue(diffQty.significantChange)

        // Promo popup appears
        val popupNode = UiNode(
            resourceId = "com.application.zomato:id/promo_popup_close",
            text = "Not now",
            className = "android.widget.Button",
            clickable = true,
            semanticRole = "button",
            bounds = Bounds(200, 400, 400, 480)
        )
        val snapshot3 = UiSnapshot(
            packageName = "com.application.zomato",
            rootNode = UiNode(children = listOf(node1Updated, popupNode))
        )
        val diffPopup = UiDiff.compute(snapshot2, snapshot3)
        assertEquals(1, diffPopup.newlyAppearedNodes.size)
        assertEquals("Not now", diffPopup.newlyAppearedNodes[0].text)
        assertTrue(diffPopup.significantChange)
    }
}
