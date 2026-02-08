# Scope64

A lightweight real-time LAB vectorscope for photography and video color grading.

![Scope64 Screenshot](screenshot.png)

## What is it?

Scope64 is a minimalist tool that displays a real-time LAB color vectorscope of any screen region. Perfect for:

- **White Balance** → Neutral grays should be centered (a=0, b=0)
- **Skin Tones** → All skin tones align on the 45° line, regardless of ethnicity
- **Color Cast Detection** → Instantly see Magenta/Green or Yellow/Blue shifts

## Why LAB?

The LAB color space separates luminosity from chrominance, making it ideal for color correction. The "skin tone line" at 45° is universal and works for all skin colors.

## Features

- Real-time vectorscope (10 FPS)
- Skin tone reference line (45°)
- Dynamic range display (L* and EV)
- Color temperature estimation (Kelvin)
- Clipping indicators (shadows/highlights)
- Dominant color detection
- Screenshot export (PNG)
- Always on top
- Cross-platform (Windows, macOS, Linux)
- Ultra lightweight (~11 KB)

## Requirements

- Java 8 or higher
- Download and install from: https://www.oracle.com/java/technologies/downloads/

## Usage
```bash
java -jar Scope64.jar
```

1. Click on the window to start selection
2. Draw a rectangle on any screen area
3. The vectorscope updates in real-time
4. Click again to select a new area

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **S** | Save screenshot to Desktop |
| **ESC** | Quit |
| **Left Click** | New selection |

## Display Info

| Position | Information |
|----------|-------------|
| Top left | Shadow clipping % Lstar < 3 |
| Top right | Highlight clipping % Lstar > 97 |
| Left | Dynamic range (D), EV stops, Color temp (K) |
| Right | Dominant color cast |

## How to Read the Vectorscope

- **Center** = Neutral (no color cast)
- **Top** = Magenta
- **Bottom** = Green
- **Right** = Yellow
- **Left** = Blue
- **45° line** = Skin tone reference

## Use Cases

### White Balance
Select a gray/neutral area. If the points are not centered, your white balance needs adjustment.

### Skin Tones
Select a skin area. Points should align along the 45° line. If they drift toward green or magenta, adjust your tint.

### Color Cast Detection
Select the entire image. The dominant color indicator shows which direction your image is shifted.

## License

MIT License - Free to use, modify, and distribute.

## Author

Olivier FABRE

Made with passion for photographers and colorists who understand the power of LAB.