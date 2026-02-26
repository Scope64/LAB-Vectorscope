import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class Scope64 {
	
	public static boolean selectionActive = false;
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			SelectionFrame.showLabAxis();
		});
	}
	
	public static double applyLabFunction(double t) {
		final double epsilon = 216.0 / 24389.0;
		final double kappa = 24389.0 / 27.0;
		
		if (t > epsilon) {
			return Math.cbrt(t);
		} else {
			return (kappa * t + 16) / 116;
		}
	}
	
	public static double[] sRGB_2_LAB(int r, int g, int b) {
		double R = r / 255.0;
		double G = g / 255.0;
		double B = b / 255.0;
		
		R = (R > 0.04045) ? Math.pow((R + 0.055) / 1.055, 2.4) : R / 12.92;
		G = (G > 0.04045) ? Math.pow((G + 0.055) / 1.055, 2.4) : G / 12.92;
		B = (B > 0.04045) ? Math.pow((B + 0.055) / 1.055, 2.4) : B / 12.92;
		
		double X = R * 0.4360747 + G * 0.3850649 + B * 0.1430804;
		double Y = R * 0.2225045 + G * 0.7168786 + B * 0.0606169;
		double Z = R * 0.0139322 + G * 0.0971045 + B * 0.7141733;
		
		X /= 0.964212;
		Y /= 1.000000;
		Z /= 0.825188;
		
		X = applyLabFunction(X);
		Y = applyLabFunction(Y);
		Z = applyLabFunction(Z);
		
		double L_val = Math.max(0, 116 * Y - 16);
		double a_val = 500 * (X - Y);
		double b_val = 200 * (Y - Z);
		
		return new double[] {L_val, a_val, b_val};
	}

	public static double[] sRGB_To_XY(int r, int g, int b) {
		double R = r / 255.0;
		double G = g / 255.0;
		double B = b / 255.0;
		
		R = (R > 0.04045) ? Math.pow((R + 0.055) / 1.055, 2.4) : R / 12.92;
		G = (G > 0.04045) ? Math.pow((G + 0.055) / 1.055, 2.4) : G / 12.92;
		B = (B > 0.04045) ? Math.pow((B + 0.055) / 1.055, 2.4) : B / 12.92;
		
		double X = R * 0.4360747 + G * 0.3850649 + B * 0.1430804;
		double Y = R * 0.2225045 + G * 0.7168786 + B * 0.0606169;
		double Z = R * 0.0139322 + G * 0.0971045 + B * 0.7141733;
		
		double SUM = X + Y + Z;
		
		if (SUM == 0) return new double[] { 0, 0 };
				
		return new double[] { X / SUM, Y / SUM };
	}
	
	public static double calculateColorTemperature(double x, double y) {
		double n = (x - 0.3320) / (0.1858 - y);
		double cct = 449 * Math.pow(n, 3) + 3525 * Math.pow(n, 2) + 6823.3 * n + 5520.33;
		return cct;
	}

	static class SelectionFrame extends JFrame {
		private final BufferedImage screenImage;
		private final Rectangle screenBounds;
		private Point startPoint;
		private static boolean selectionMade = false;
		private static boolean isDragging = false;
		private static Point globalStartPoint;
		private static List<SelectionFrame> allFrames;
		private static Timer globalMouseTracker;
		private ImagePanel imagePanel;
		
		private static JFrame resultFrame = null;
		private static JLabel imageLabel = null;
		private static BufferedImage lastCapture = null;
		
		private static int avgR = 0;
		private static int avgG = 0;
		private static int avgB = 0;
		
		private static double avgLstar = 0;
		private static double avgAstar = 0;
		private static double avgBstar = 0;
		
		private static double minLstar = 100;
		private static double maxLstar = 0;
		
		private static double percentLowL = 0;
		private static double percentHighL = 0;
		
		private static double dynamicRange = 0;
		private static double dynamicRangeEV = 0;
		
		private static double colorTemp = 5000;
		
		private static Timer refreshTimer = null;
		private static Rectangle lastSelection = null;
		
		private static boolean showZoneSystem = false;
		
		public SelectionFrame(BufferedImage image, Rectangle bounds) {
			this.screenImage = image;
			this.screenBounds = bounds;
			
			setUndecorated(true);
			setAlwaysOnTop(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			setBounds(bounds);
			
			getRootPane().registerKeyboardAction(e -> {
				cancelSelection();
			}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
			
			imagePanel = new ImagePanel();
			setContentPane(imagePanel);
			
			MouseAdapter mouseHandler = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					if (selectionMade) return;
					
					globalStartPoint = new Point(e.getX() + screenBounds.x, e.getY() + screenBounds.y);
					startPoint = e.getPoint();
					isDragging = true;
					startGlobalMouseTracking();
				}
				
				@Override
				public void mouseReleased(MouseEvent e) {
					if (selectionMade) return;
					
					stopGlobalMouseTracking();
					isDragging = false;
					
					if (startPoint == null) return;
					
					Point globalEndPoint = new Point(e.getX() + screenBounds.x, e.getY() + screenBounds.y);
					Rectangle globalSelection = createGlobalRectangle(globalStartPoint, globalEndPoint);
					
					if (globalSelection.width < 5 || globalSelection.height < 5) {
						clearAllSelections();
						return;
					}
					
					selectionMade = true;
					clearAllSelections();
					
					Timer captureTimer = new Timer(50, evt -> {
						Scope64.selectionActive = false;
						closeAllSelectionFrames();
						
						SwingUtilities.invokeLater(() -> {
							startRealTimeUpdate(globalSelection);
							resultFrame.setVisible(true);
							resultFrame.requestFocus();
						});
						
						((Timer)evt.getSource()).stop();
					});
					captureTimer.setRepeats(false);
					captureTimer.start();
				}
				
				@Override
				public void mouseDragged(MouseEvent e) {
					if (selectionMade || !isDragging || startPoint == null) return;
					
					Point endPoint = e.getPoint();
					Rectangle selectionRect = createGlobalRectangle(startPoint, endPoint);
					imagePanel.setSelection(selectionRect);
					imagePanel.repaint();
				}
			};
			
			imagePanel.addMouseListener(mouseHandler);
			imagePanel.addMouseMotionListener(mouseHandler);
		}
		
		private static void startRealTimeUpdate(Rectangle selection) {
			lastSelection = selection;
			
			if (refreshTimer != null) {
				refreshTimer.stop();
			}
			
			refreshTimer = new Timer(30, e -> {
				try {
					Robot robot = new Robot();
					lastCapture = robot.createScreenCapture(lastSelection);
					
					BufferedImage zoneImage = showZoneSystem
								? createZoneSystemImage(500)
								: createLabAxisImage(500);
					imageLabel.setIcon(new ImageIcon(zoneImage));	
					
				} catch (AWTException ex) {
					ex.printStackTrace();
				}
			});
			refreshTimer.start();
		}
		
		private static void saveScreenshot() {
			if (resultFrame == null) return;
			
			try {
				BufferedImage screenshot = new BufferedImage(
					resultFrame.getContentPane().getWidth(),
					resultFrame.getContentPane().getHeight(),
					BufferedImage.TYPE_INT_ARGB
				);
				resultFrame.getContentPane().paint(screenshot.getGraphics());
				
				String desktop = System.getProperty("user.home") + "/Desktop/";
				String filename = "Scope64_" + System.currentTimeMillis() + ".png";
				File file = new File(desktop + filename);
				
				ImageIO.write(screenshot, "PNG", file);				   
				
			} catch (Exception ex) {
				System.err.println("Error : " + ex.getMessage());
			}
		}
		
		public static void showLabAxis() {
			if (resultFrame == null) {
				resultFrame = new JFrame("Scope64 - Copyright (c) 1996- " + java.time.Year.now().getValue() + " - Olivier FABRE");
				resultFrame.setResizable(false);
				resultFrame.setAlwaysOnTop(true);
				resultFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
				
				resultFrame.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent e) {
						if (refreshTimer != null) {
							refreshTimer.stop();
						}
						System.exit(0);
					}
				});
				
				resultFrame.addKeyListener(new KeyAdapter() {
					@Override
					public void keyPressed(KeyEvent e) {
						if (e.getKeyCode() == KeyEvent.VK_S) {
							saveScreenshot();
						}
						if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
							if (refreshTimer != null) {
								refreshTimer.stop();
							}
							System.exit(0);
						}
						if (e.getKeyCode() == KeyEvent.VK_SPACE) {
							showZoneSystem = !showZoneSystem;
							
							BufferedImage zoneImage = showZoneSystem
								? createZoneSystemImage(500)
								: createLabAxisImage(500);
							imageLabel.setIcon(new ImageIcon(zoneImage));	
							
						}
						
					}
				});
				resultFrame.setFocusable(true);
				
				imageLabel = new JLabel();
				
				imageLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if ( (e.getButton() == MouseEvent.BUTTON1) || (e.getButton() == MouseEvent.BUTTON3) ) {
							launchSelection();
						}
					}
				});
				
				resultFrame.add(imageLabel);
			}
			
			BufferedImage labImage = createLabAxisImage(500);
			 
			imageLabel.setIcon(new ImageIcon(labImage));
						 
			resultFrame.pack();
			resultFrame.setLocationRelativeTo(null);
			resultFrame.setVisible(true);
			resultFrame.requestFocus();
		}
		
		private static BufferedImage createLabAxisImage(int size) {
			BufferedImage labImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = labImage.createGraphics();
			
			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, size, size);
			
			int centerX = size / 2;
			int centerY = size / 2;
			int radius = size / 2 - 20;
			
			drawLabAxis(g2d, centerX, centerY, radius);
			plotLabPoints(g2d, lastCapture, centerX, centerY, radius);
			
			g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
			FontMetrics metrics = g2d.getFontMetrics();
						
			String lowLText = String.format("%.2f", percentLowL);
			g2d.setColor(percentLowL >= 1 ? Color.WHITE : new Color(128, 128, 128));
			drawText(g2d, lowLText, 10, 20);
						
			g2d.setColor(new Color(128, 128, 128));
			drawText(g2d, String.format("D  %.1f", dynamicRange), 10, 40);
			drawText(g2d, String.format("EV  %.1f", dynamicRangeEV), 10, 60);
						
			String colorTempText = (colorTemp < 0 || colorTemp > 10000) ? "K" : String.format("K  %.0f", colorTemp);
			drawText(g2d, colorTempText, 10, 80);
						
			String highLText = String.format("%.2f", percentHighL);
			g2d.setColor(percentHighL >= 1 ? Color.WHITE : new Color(128, 128, 128));
			drawText(g2d, highLText, size - metrics.stringWidth(highLText) - 10, 20);
						
			g2d.setColor(new Color(128, 128, 128));
			String[] colorTexts = getColorTexts();
			drawText(g2d, colorTexts[0], size - metrics.stringWidth(colorTexts[0]) - 10, 40);
			drawText(g2d, colorTexts[1], size - metrics.stringWidth(colorTexts[1]) - 10, 60);
			
			g2d.dispose();
			return labImage;
		}
		
		private static BufferedImage createZoneSystemImage(int size) {
			BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = img.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, size, size);			  

			double[] zoneBounds = { 0, 5, 15, 25, 35, 45, 55, 65, 75, 85, 95, 100 };

			double[] zonePercent = new double[11];
			int[]	 zoneCount	 = new int[11];

			minLstar = 100;
			maxLstar = 0;

			int countLowL = 0;
			int countHighL = 0;

			if (lastCapture != null) {
				int nbPixels = lastCapture.getWidth() * lastCapture.getHeight();
				for (int y = 0; y < lastCapture.getHeight(); y++) {
					for (int x = 0; x < lastCapture.getWidth(); x++) {
						int rgb = lastCapture.getRGB(x, y);
						int r	= (rgb >> 16) & 0xFF;
						int g	= (rgb >> 8)  & 0xFF;
						int b	=  rgb		  & 0xFF;
						double L = Scope64.sRGB_2_LAB(r, g, b)[0];
						
						if (L < 3.0) countLowL++;
						if (L > 97.0) countHighL++;
						
						minLstar = Math.min(minLstar, L);
						maxLstar = Math.max(maxLstar, L);
						
						for (int z = 0; z < 11; z++) {
							if (L >= zoneBounds[z] && L < zoneBounds[z + 1]) {
								zoneCount[z]++;
								break;
							}
						}
						if (L >= 95) zoneCount[10]++;
					}
				}
				for (int z = 0; z < 11; z++) {
					zonePercent[z] = (double) zoneCount[z] / nbPixels * 100.0;
				}
				
				percentLowL = (double) countLowL / nbPixels * 100.0;
				percentHighL = (double) countHighL / nbPixels * 100.0;
				
				dynamicRange = maxLstar - minLstar;
			
				double minL = Math.max(0.005, minLstar);
				double maxL = Math.max(0.005, maxLstar);
				dynamicRangeEV = Math.log(maxL / minL) / Math.log(2);
				
			}

			

			double maxPercent = 0.0;
			for (int z = 0; z < 11; z++) {
				if (zonePercent[z] > maxPercent) maxPercent = zonePercent[z];
			}

			int rectW = 396;
			int rectH = 220;
			int rectX = (size - rectW) / 2;
			int rectY = 140;
			int zoneW = rectW / 11;
			
			g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
			FontMetrics metrics = g2d.getFontMetrics();
						
			String lowLText = String.format("%.2f", percentLowL);
			g2d.setColor(percentLowL >= 1 ? Color.WHITE : new Color(128, 128, 128));
			drawText(g2d, lowLText, 10, 20);
			
			g2d.setColor(new Color(128, 128, 128));
			drawText(g2d, String.format("D  %.1f", dynamicRange), 10, 40);
			drawText(g2d, String.format("EV  %.1f", dynamicRangeEV), 10, 60);
			
			String highLText = String.format("%.2f", percentHighL);
			g2d.setColor(percentHighL >= 1 ? Color.WHITE : new Color(128, 128, 128));
			drawText(g2d, highLText, size - metrics.stringWidth(highLText) - 10, 20);			
			
			g2d.setFont(new Font("SansSerif", Font.PLAIN, 13));
			FontMetrics fmTitle = g2d.getFontMetrics();
			String title = "Z o n e	   S y s t e m";
			g2d.setColor(new Color(128, 128, 128));
			g2d.drawString(title, (size - fmTitle.stringWidth(title)) / 2, rectY - 15);
			
			g2d.setColor(new Color(50, 50, 50));
			g2d.setStroke(new BasicStroke(1.0f));
			g2d.drawRect(rectX, rectY, rectW, rectH);

			for (int z = 0; z < 11; z++) {
				double Lmid = (zoneBounds[z] + zoneBounds[z + 1]) / 2.0;
				int gray	= (int) Math.round((Lmid / 100.0) * 255.0);
				gray		= Math.max(0, Math.min(255, gray));

				int zx = rectX + z * zoneW;

				int barH = 0;
				if (maxPercent > 0) {
					barH = (int) Math.round((zonePercent[z] / maxPercent) * rectH * 0.9);
				}
				int barY = rectY + rectH - barH;

				g2d.setColor(Color.BLACK);
				g2d.fillRect(zx + 1, rectY + 1, zoneW - 1, rectH - barH - 1);

				g2d.setColor(new Color(gray, gray, gray));
				if (barH > 0) g2d.fillRect(zx + 1, barY, zoneW - 1, barH);

				g2d.setColor(new Color(32, 32, 32));
				g2d.setStroke(new BasicStroke(1.0f));
				g2d.drawRect(zx, rectY, zoneW, rectH);

				g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
				FontMetrics fm = g2d.getFontMetrics();
				String label = String.valueOf(z);
				int lx = zx + (zoneW - fm.stringWidth(label)) / 2;
				int ly = rectY + rectH + 16;
				g2d.setColor(new Color(200, 200, 200));
				g2d.drawString(label, lx, ly + 3);

				g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
				fm = g2d.getFontMetrics();
				String pLabel = zoneCount[z] > 0 ? String.format("%.1f", zonePercent[z]) : "-";
				int llx = zx + (zoneW - fm.stringWidth(pLabel)) / 2;
				g2d.setColor(new Color(100, 100, 100));
				g2d.drawString(pLabel, llx, ly + 20);
			}			

			g2d.dispose();
			return img;
		}
		
		private static String[] getColorTexts() {
			if (lastCapture == null) {
				return new String[] {"", ""};
			}
			
			if (avgAstar == 0 && avgBstar == 0) {
				return new String[] {"", ""};
			}
			
			String colorTextA = "";
			String colorTextB = "";
			
			double absAvgAstar = Math.abs(Math.round(avgAstar * 100.0) / 100.0);
			double absAvgBstar = Math.abs(Math.round(avgBstar * 100.0) / 100.0);
			
			boolean aPositive = avgAstar > 0;
			boolean bPositive = avgBstar > 0;
			boolean aGreater = Math.abs(avgAstar) > Math.abs(avgBstar);
			boolean aEqual = Math.abs(avgAstar) == Math.abs(avgBstar);
			
			if (aPositive && bPositive) {
				colorTextA = aGreater ? "M a g e n t a  ++" : (aEqual ? "M a g e n t a  ==" : "M a g e n t a");
				colorTextB = aGreater ? "Y e l l o w" : (aEqual ? "Y e l l o w" : "Y e l l o w  ++");
			} else if (!aPositive && bPositive) {
				colorTextA = aGreater ? "G r e e n  ++" : (aEqual ? "G r e e n  ==" : "G r e e n");
				colorTextB = aGreater ? "Y e l l o w" : (aEqual ? "Y e l l o w" : "Y e l l o w  ++");
			} else if (!aPositive && !bPositive) {
				colorTextA = aGreater ? "G r e e n  ++" : (aEqual ? "G r e e n  ==" : "G r e e n");
				colorTextB = aGreater ? "B l u e" : (aEqual ? "B l u e" : "B l u e  ++");
			} else if (aPositive && !bPositive) {
				colorTextA = aGreater ? "M a g e n t a  ++" : (aEqual ? "M a g e n t a  ==" : "M a g e n t a");
				colorTextB = aGreater ? "B l u e" : (aEqual ? "B l u e" : "B l u e  ++");
			}
			
			return new String[] {colorTextA, colorTextB};
		}
		
		private static Color interpolateColor(Color c1, Color c2, float ratio) {
			int r = (int)(c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
			int g = (int)(c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
			int b = (int)(c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
			return new Color(r, g, b);
		}
		
		private static void drawLabAxis(Graphics2D g2d, int centerX, int centerY, int radius) {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			
			Color centerColor = new Color(32, 32, 32);
			int thickness = 1;
			
			for (int y = centerY - radius; y <= centerY + radius; y++) {
				for (int x = centerX - thickness/2; x <= centerX + thickness/2; x++) {
					double distance = Math.abs(y - centerY);
					if (distance <= radius) {
						float ratio = (float)(distance / radius);
						Color color;
						if (y < centerY) {
							color = interpolateColor(centerColor, new Color(255, 0, 149), ratio);
						} else if (y > centerY) {
							color = interpolateColor(centerColor, new Color(0, 155, 116), ratio);
						} else {
							color = centerColor;
						}
						g2d.setColor(color);
						g2d.fillRect(x, y, 2, 2);
					}
				}
			}
			
			for (int x = centerX - radius; x <= centerX + radius; x++) {
				for (int y = centerY - thickness/2; y <= centerY + thickness/2; y++) {
					double distance = Math.abs(x - centerX);
					if (distance <= radius) {
						float ratio = (float)(distance / radius);
						Color color;
						if (x > centerX) {
							color = interpolateColor(centerColor, new Color(255, 238, 0), ratio);
						} else if (x < centerX) {
							color = interpolateColor(centerColor, new Color(0, 96, 255), ratio);
						} else {
							color = centerColor;
						}
						g2d.setColor(color);
						g2d.fillRect(x, y, 2, 2);
					}
				}
			}
			
			drawSkinToneLabLine(g2d, centerX, centerY, radius);
		}
 
		private static void drawSkinToneLabLine(Graphics2D g2d, int centerX, int centerY, int radius) {
			g2d.setColor(new Color(32, 32, 32));
			g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			
			int shortenedRadius = radius - 1;
			double angle1 = Math.toRadians(45);
			int x1 = centerX + (int) Math.round(shortenedRadius * Math.sin(angle1));
			int y1 = centerY - (int) Math.round(shortenedRadius * Math.cos(angle1));
			g2d.drawLine(centerX + 1, centerY, x1, y1);
		}
 
		private static void drawText(Graphics2D g2d, String text, int x, int y) {
			FontMetrics metrics = g2d.getFontMetrics();
			int textWidth = metrics.stringWidth(text);
			int textHeight = metrics.getHeight();
			
			Color textColor = g2d.getColor();
			g2d.setColor(new Color(0, 0, 0, 180));
			g2d.fillRoundRect(x - 3, y - textHeight + 3, textWidth + 6, textHeight + 2, 5, 5);
			
			g2d.setColor(textColor);
			g2d.drawString(text, x, y);
		}
 
		private static void plotLabPoints(Graphics2D g2d, BufferedImage image, int centerX, int centerY, int maxRadius) {
			if (image == null) return;
						
			minLstar = 100;
			maxLstar = 0;
			
			long totalR = 0, totalG = 0, totalB = 0;
			int nbPixels = image.getWidth() * image.getHeight();
			int countLowL = 0;
			int countHighL = 0;
			
			double maxA = 80.0;
			double maxB = 80.0;
			
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int rgb = image.getRGB(x, y);
					
					int r = (rgb >> 16) & 0xFF;
					int g = (rgb >> 8) & 0xFF;
					int b = rgb & 0xFF;
					
					totalR += r;
					totalG += g;
					totalB += b;
					
					double[] lab = Scope64.sRGB_2_LAB(r, g, b);
					double L = lab[0];
					double A = lab[1];
					double B = lab[2];
					
					minLstar = Math.min(minLstar, L);
					maxLstar = Math.max(maxLstar, L);
					
					if (L < 3.0) countLowL++;
					if (L > 97.0) countHighL++;
					
					int plotX = centerX + (int) Math.round((B / maxB) * maxRadius);
					int plotY = centerY - (int) Math.round((A / maxA) * maxRadius);
					
					if (plotX >= 0 && plotX < centerX * 2 && plotY >= 0 && plotY < centerY * 2) {
						int alpha = 30 + (int)(L * 1.5);
						alpha = Math.min(150, alpha);
						g2d.setColor(new Color(r, g, b, alpha));
						g2d.fillRect(plotX, plotY, 1, 1);
					}
				}
			}
			
			avgR = (int) (totalR / nbPixels);
			avgG = (int) (totalG / nbPixels);
			avgB = (int) (totalB / nbPixels);
			
			double[] avgLab = Scope64.sRGB_2_LAB(avgR, avgG, avgB);
			avgLstar = avgLab[0];
			avgAstar = avgLab[1];
			avgBstar = avgLab[2];
			
			percentLowL = (double) countLowL / nbPixels * 100.0;
			percentHighL = (double) countHighL / nbPixels * 100.0;
			
			dynamicRange = maxLstar - minLstar;
			
			double minL = Math.max(0.005, minLstar);
			double maxL = Math.max(0.005, maxLstar);
			dynamicRangeEV = Math.log(maxL / minL) / Math.log(2);
			
			double[] xy = sRGB_To_XY(avgR, avgG, avgB);
			colorTemp = calculateColorTemperature(xy[0], xy[1]);
		}
		
		private static void cancelSelection() {
			Scope64.selectionActive = false;
			closeAllSelectionFrames();
			SwingUtilities.invokeLater(() -> {
				resultFrame.setVisible(true);
				resultFrame.requestFocus();
			});
		}
		
		public static void launchSelection() {
			Scope64.selectionActive = true;
			
			if (refreshTimer != null) {
				refreshTimer.stop();
			}
			
			if (resultFrame != null) {
				resultFrame.setVisible(false);
			}
			
			try {
				Robot robot = new Robot();
				GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
				GraphicsDevice[] screens = ge.getScreenDevices();
				
				List<SelectionFrame> selectionFrames = new ArrayList<>();
				
				for (GraphicsDevice screen : screens) {
					Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
					BufferedImage screenImage = robot.createScreenCapture(screenBounds);
					SelectionFrame selectionFrame = new SelectionFrame(screenImage, screenBounds);
					selectionFrames.add(selectionFrame);
					selectionFrame.setVisible(true);
					selectionFrame.requestFocus();
					selectionFrame.toFront();
				}
				
				allFrames = selectionFrames;
				
			} catch (AWTException ex) {
				ex.printStackTrace();
				Scope64.selectionActive = false;
			}
		}
		
		private static void startGlobalMouseTracking() {
			if (globalMouseTracker != null && globalMouseTracker.isRunning()) {
				globalMouseTracker.stop();
			}
			
			globalMouseTracker = new Timer(16, e -> {
				if (!isDragging || globalStartPoint == null) return;
				
				try {
					Point currentMousePos = MouseInfo.getPointerInfo().getLocation();
					updateAllFramesSelection(globalStartPoint, currentMousePos);
				} catch (Exception ex) {}
			});
			globalMouseTracker.start();
		}
		
		private static void stopGlobalMouseTracking() {
			if (globalMouseTracker != null && globalMouseTracker.isRunning()) {
				globalMouseTracker.stop();
			}
		}
		
		private static void updateAllFramesSelection(Point globalStart, Point globalEnd) {
			if (allFrames == null) return;
			
			Rectangle globalSelection = createGlobalRectangle(globalStart, globalEnd);
			
			for (SelectionFrame frame : allFrames) {
				Rectangle intersection = globalSelection.intersection(frame.screenBounds);
				
				if (intersection.width > 0 && intersection.height > 0) {
					Rectangle localSelection = new Rectangle(
						intersection.x - frame.screenBounds.x,
						intersection.y - frame.screenBounds.y,
						intersection.width,
						intersection.height
					);
					frame.getImagePanel().setSelection(localSelection);
				} else {
					frame.getImagePanel().setSelection(null);
				}
				frame.getImagePanel().repaint();
			}
		}
		
		private static Rectangle createGlobalRectangle(Point p1, Point p2) {
			return new Rectangle(
				Math.min(p1.x, p2.x),
				Math.min(p1.y, p2.y),
				Math.abs(p1.x - p2.x),
				Math.abs(p1.y - p2.y)
			);
		}
		
		private static void clearAllSelections() {
			if (allFrames == null) return;
			for (SelectionFrame frame : allFrames) {
				frame.getImagePanel().setSelection(null);
				frame.getImagePanel().repaint();
			}
		}
		
		private static void closeAllSelectionFrames() {
			stopGlobalMouseTracking();
			if (allFrames != null) {
				for (SelectionFrame frame : allFrames) {
					frame.dispose();
				}
			}
			selectionMade = false;
			isDragging = false;
			globalStartPoint = null;
		}
		
		public ImagePanel getImagePanel() {
			return imagePanel;
		}
		
		class ImagePanel extends JPanel {
			private Rectangle selection;
			
			public void setSelection(Rectangle rect) {
				this.selection = rect;
			}
			
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.drawImage(screenImage, 0, 0, null);
				
				if (selection != null) {
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2d.setColor(new Color(0, 0, 255, 50));
					g2d.fill(selection);
					g2d.setColor(Color.BLUE);
					g2d.draw(selection);
				}
			}
		}
	}
}