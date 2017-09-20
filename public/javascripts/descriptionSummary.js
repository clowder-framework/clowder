/**
 * Created by myersjd
 * 
 * These methods look for fields of class 'summary' and limit them to numrows
 * in height. To do this reliably when the font family and size are dynamic
 * (e.g. by theme) and when the abstract text can include linefeeds/blank lines
 * and encoded html characters requires: - identifying the font family in use
 * and the font size - looking at the width of each line, as it will be
 * displayed, in comparison to the width of the element - accounting for
 * wrap-around of longer lines, - taking into account that wrapping is on word
 * boundaries (per current css) - re-adjusting upon window resize, - adding a
 * '...' at the end of a truncated item (using a span of class ellipses so that
 * it can be distinguished from the text), while accounting for the added length
 * of the '...' string
 * 
 * 
 * 
 */

var usingDefaultFont = false;

$(function() {
	var numrows = 8;
	var canvas = $("<canvas/>")[0];
	var context = canvas.getContext('2d');
	if ($(".abstractsummary").length != 0) {
		context.font = $(".abstractsummary").detectFont();
		// Try once to see if the font has loaded. Better mechanism would be to
		// use
		// aWebFontLoader but this means changing how themes work (they have a
		// font
		// import line now)
		if (usingDefaultFont) {
			console.log("Checking for slow font");
			setTimeout(function() {
				context.font = $(".abstractsummary").detectFont();
				summarizeAbstracts(numrows, context);
			}, 500);
		}
		summarizeAbstracts(numrows, context);
		$(window).resize(function() {
			context.font = $(".abstractsummary").detectFont();
			summarizeAbstracts(numrows, context);
		});
	}
});

//Wrapper to call on-demand, e.g. when a new tab is showm
function doSummarizeAbstracts() {
	var numrows = 8;
	var canvas = $("<canvas/>")[0];
	var context = canvas.getContext('2d');
	if ($(".abstractsummary").length != 0) {
		context.font = $(".abstractsummary").detectFont();
		summarizeAbstracts(numrows, context);
	}	
}

function summarizeAbstracts(lines, context) {
	$(".abstractsummary")
			.each(
					function(index, element) {
						// Copy original text to a data element so it can be
						// used again when resizing
						var text = $(this).attr("data-original-text");
						if (text == null) {
							text = $(this).html();

							$(this).attr("data-original-text", text);
						}
						// Get the element width (which may be rounded up)
						var parawidth = $(this).width();
						if (parawidth != 0) {
							// Look for line breaks and consider the text 1 line
							// at a time
							var textLines = text.split("<br>");
							text = "";
							var count = 0;
							var curLine = 0;
							while (count < lines && curLine < textLines.length) {
								// Get the width of the current line with any
								// html decoded
								var metrics = context
										.measureText(htmlDecode(textLines[curLine]));
								var width = metrics.width;

								if (width < parawidth) {
									// For lines shorter than the element width,
									// include them up to the row limit
									if (count == (lines - 1)
											&& curLine < (textLines.length - 1)) {
										// If we're at the row limit and there
										// are more lines, indicate the
										// truncation
										// Truncate to add ellipses if needed
										text = text
												+ htmlEncode(truncateForEllipses(
														htmlDecode(textLines[curLine]),
														context, parawidth - 1))
												+ $('<span/>')
														.text("...")
														.addClass("ellipses")
														.attr("title",
																"text truncated in this view")[0].outerHTML;
									} else {
										// Not at row limit, just add text
										// (original html encoded)
										text = text + textLines[curLine];
									}
									// If we're not at the row limit, add the
									// line break that was removed in splitting
									// the text
									if (curLine < (textLines.length - 1)
											&& count < lines) {
										text = text + "<br>";
									}
									// A line has been added to the summary and
									// a line of text has been consumed
									count = count + 1;
									curLine = curLine + 1;
								} else {
									// The current line is wider than the
									// element width, so figure out how many
									// characters fit in one width, add those as
									// a line, and use the remainder of the line
									// as the source text in the next pass
									// through the loop
									// Work on decoded text
									var curText = htmlDecode(textLines[curLine]);
									var fits = fitWidth(curText, parawidth,
											context);
									// If we're at the row limit, we have to
									// truncate
									if (count == (lines - 1)) {
										// Truncate to add ellipses if needed
										text = text
												+ htmlEncode(truncateForEllipses(
														curText.substring(0,
																fits), context,
														parawidth - 1))
												+ $('<span/>')
														.text("...")
														.addClass("ellipses")
														.attr("title",
																"text truncated in this view")[0].outerHTML;
									} else {
										// If not, we need to add a row of text
										// (encoded) and use the remainder of
										// this line as the source for the next
										// row
										text = text
												+ htmlEncode(curText.substring(
														0, fits));
									}
									textLines[curLine] = htmlEncode(curText
											.substring(fits));
									// Added a line to the summary but did not
									// consume a row of the original text
									count = count + 1;
								}
							}
							// Add the summary as the displayed value (note that
							// it includes linefeeds and possibly a span
							// element, so is html, not text
							$(this).html(text);
						}
					})
}

/*
 * Binary search to find the maximum number of characters that will definitely
 * fit in the given width Caller should send a width 1 less than the value
 * retrieved for the enclosing element to account for rounding
 */
function fitWidth(text, width, context) {

	var length = text.length / 2;
	var step = length / 2;
	// width = Math.floor(width);
	var curWidth = context.measureText(text.substring(0, length)).width;
	var wordOffset = 0;
	lastWidth = -1;
	while ((step >= 0.5) || curWidth > width) {

		// alert(curWidth +" : "+width+" : "+length+" : "+step);
		if (curWidth >= width) {
			length = length - step + wordOffset;
			// Account for an assumed css break at word boundaries style
			var wordlength = length;
			while (wordlength > 0 && text.charAt(wordlength) != ' ') {
				wordlength--;
			}
			if (wordlength > 0) {
				wordOffset = (length - wordlength - 1);
				length = wordlength + 1;
			} else {
				wordOffset = 0;
			}

		} else {
			length = length + step + wordOffset;
			// Account for an assumed css break at word boundaries style
			var wordlength = length;
			while (wordlength > 0 && text.charAt(wordlength) != ' ') {
				wordlength--;
			}
			if (wordlength > 0) {
				wordOffset = (length - wordlength - 1);
				length = wordlength + 1;
			} else {
				wordOffset = 0;
			}
		}
		step = step / 2;
		curWidth = context.measureText(text.substring(0, length)).width;
		if (lastWidth == curWidth) {
			// If curWidth stops incrementing, step is just approaching limit=0 so break out to finish loading
			break;
		}
		lastWidth = curWidth;

	}
	return length;
}

function truncateForEllipses(text, context, parawidth) {
	while (context.measureText(text + "...").width >= parawidth) {
		text = text.substring(0, text.length - 1);
	}
	return text;

}

/**
 * Detects the font of an element from the font-family css attribute by
 * comparing the font widths on the element
 * 
 * @link http://stackoverflow.com/questions/15664759/jquery-how-to-get-assigned-font-to-element
 */
(function($) {
	$.fn.detectFont = function() {
		var fontfamily = $(this).css('font-family');
		var fontsize = $(this).css('font-size');
		var fonts = fontfamily.split(',');
		if (fonts.length == 1)
			return fonts[0];

		var element = $(this);
		var detectedFont = null;
		fonts.forEach(function(font) {
			var clone = $('<span>wwwwwwwwwwwwwwwlllllllliiiiii</span>').css({
				'font-family' : fontfamily,
				'font-size' : '70px',
				'display' : 'inline',
				'visibility' : 'hidden'
			}).appendTo('body');
			var dummy = $('<span>wwwwwwwwwwwwwwwlllllllliiiiii</span>').css({
				'font-family' : font,
				'font-size' : '70px',
				'display' : 'inline',
				'visibility' : 'hidden'
			}).appendTo('body');
			if (clone.width() == dummy.width())
				detectedFont = font;
			clone.remove();
			dummy.remove();
		});
		if (detectedFont != fonts[0]) {
			usingDefaultFont = true;
		}
		return fontsize + " " + detectedFont;
	}
})(jQuery);