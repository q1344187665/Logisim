/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.draw.model;

import java.awt.Color;
import java.awt.Graphics;
import java.util.List;
import java.util.Random;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.cburch.draw.shapes.DrawAttr;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.util.EventSourceWeakSupport;
import com.cburch.logisim.util.GraphicsUtil;

public abstract class AbstractCanvasObject implements AttributeSet, CanvasObject, Cloneable {
	private static final int OVERLAP_TRIES = 50;
	private static final int GENERATE_RANDOM_TRIES = 20;

	private EventSourceWeakSupport<AttributeListener> listeners;

	public AbstractCanvasObject() {
		listeners = new EventSourceWeakSupport<AttributeListener>();
	}

	@Override
	public void addAttributeListener(AttributeListener l) {
		listeners.add(l);
	}

	@Override
	public Handle canDeleteHandle(Location loc) {
		return null;
	}

	@Override
	public Handle canInsertHandle(Location desired) {
		return null;
	}

	@Override
	public boolean canMoveHandle(Handle handle) {
		return false;
	}

	@Override
	public boolean canRemove() {
		return true;
	}

	@Override
	public CanvasObject clone() {
		try {
			AbstractCanvasObject ret = (AbstractCanvasObject) super.clone();
			ret.listeners = new EventSourceWeakSupport<AttributeListener>();
			return ret;
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	@Override
	public abstract boolean contains(Location loc, boolean assumeFilled);

	@Override
	public boolean containsAttribute(Attribute<?> attr) {
		return getAttributes().contains(attr);
	}

	@Override
	public Handle deleteHandle(Handle handle) {
		throw new UnsupportedOperationException("deleteHandle");
	}

	protected void fireAttributeListChanged() {
		AttributeEvent e = new AttributeEvent(this);
		for (AttributeListener listener : listeners) {
			listener.attributeListChanged(e);
		}
	}

	@Override
	public Attribute<?> getAttribute(String name) {
		for (Attribute<?> attr : getAttributes()) {
			if (attr.getName().equals(name))
				return attr;
		}
		return null;
	}

	// methods required by AttributeSet interface
	@Override
	public abstract List<Attribute<?>> getAttributes();

	@Override
	public AttributeSet getAttributeSet() {
		return this;
	}

	@Override
	public abstract Bounds getBounds();

	@Override
	public abstract String getDisplayName();

	@Override
	public abstract List<Handle> getHandles(HandleGesture gesture);

	protected Location getRandomPoint(Bounds bds, Random rand) {
		int x = bds.getX();
		int y = bds.getY();
		int w = bds.getWidth();
		int h = bds.getHeight();
		for (int i = 0; i < GENERATE_RANDOM_TRIES; i++) {
			Location loc = Location.create(x + rand.nextInt(w), y + rand.nextInt(h));
			if (contains(loc, false))
				return loc;
		}
		return null;
	}

	@Override
	public abstract <V> V getValue(Attribute<V> attr);

	@Override
	public void insertHandle(Handle desired, Handle previous) {
		throw new UnsupportedOperationException("insertHandle");
	}

	@Override
	public boolean isReadOnly(Attribute<?> attr) {
		return false;
	}

	@Override
	public boolean isToSave(Attribute<?> attr) {
		return true;
	}

	@Override
	public abstract boolean matches(CanvasObject other);

	@Override
	public abstract int matchesHashCode();

	@Override
	public Handle moveHandle(HandleGesture gesture) {
		throw new UnsupportedOperationException("moveHandle");
	}

	@Override
	public boolean overlaps(CanvasObject other) {
		Bounds a = this.getBounds();
		Bounds b = other.getBounds();
		Bounds c = a.intersect(b);
		Random rand = new Random();
		if (c.getWidth() == 0 || c.getHeight() == 0) {
			return false;
		} else if (other instanceof AbstractCanvasObject) {
			AbstractCanvasObject that = (AbstractCanvasObject) other;
			for (int i = 0; i < OVERLAP_TRIES; i++) {
				if (i % 2 == 0) {
					Location loc = this.getRandomPoint(c, rand);
					if (loc != null && that.contains(loc, false))
						return true;
				} else {
					Location loc = that.getRandomPoint(c, rand);
					if (loc != null && this.contains(loc, false))
						return true;
				}
			}
			return false;
		} else {
			for (int i = 0; i < OVERLAP_TRIES; i++) {
				Location loc = this.getRandomPoint(c, rand);
				if (loc != null && other.contains(loc, false))
					return true;
			}
			return false;
		}
	}

	@Override
	public abstract void paint(Graphics g, HandleGesture gesture);

	@Override
	public void removeAttributeListener(AttributeListener l) {
		listeners.remove(l);
	}

	protected boolean setForFill(Graphics g) {
		List<Attribute<?>> attrs = getAttributes();
		if (attrs.contains(DrawAttr.PAINT_TYPE)) {
			Object value = getValue(DrawAttr.PAINT_TYPE);
			if (value == DrawAttr.PAINT_STROKE)
				return false;
		}

		Color color = getValue(DrawAttr.FILL_COLOR);
		if (color != null && color.getAlpha() == 0) {
			return false;
		} else {
			if (color != null)
				g.setColor(color);
			return true;
		}
	}

	protected boolean setForStroke(Graphics g) {
		List<Attribute<?>> attrs = getAttributes();
		if (attrs.contains(DrawAttr.PAINT_TYPE)) {
			Object value = getValue(DrawAttr.PAINT_TYPE);
			if (value == DrawAttr.PAINT_FILL)
				return false;
		}

		Integer width = getValue(DrawAttr.STROKE_WIDTH);
		if (width != null && width.intValue() > 0) {
			Color color = getValue(DrawAttr.STROKE_COLOR);
			if (color != null && color.getAlpha() == 0) {
				return false;
			} else {
				GraphicsUtil.switchToWidth(g, width.intValue());
				if (color != null)
					g.setColor(color);
				return true;
			}
		} else {
			return false;
		}
	}

	@Override
	public void setReadOnly(Attribute<?> attr, boolean value) {
		throw new UnsupportedOperationException("setReadOnly");
	}

	@Override
	public final <V> void setValue(Attribute<V> attr, V value) {
		Object old = getValue(attr);
		boolean same = old == null ? value == null : old.equals(value);
		if (!same) {
			updateValue(attr, value);
			AttributeEvent e = new AttributeEvent(this, attr, value);
			for (AttributeListener listener : listeners) {
				listener.attributeValueChanged(e);
			}
		}
	}

	public abstract Element toSvgElement(Document doc);

	@Override
	public abstract void translate(int dx, int dy);

	protected abstract void updateValue(Attribute<?> attr, Object value);

}
