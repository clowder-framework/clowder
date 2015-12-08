(function ($, Configuration) {
  console.log("PTM previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  var height = 600;  
  var width = 750;
  
  $(Configuration.tab).append("<br/>");
  
  $(Configuration.tab).append(
		  "<object name='ptmApplet' type='application/x-java-applet' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='code' value='jpview.gui.Standalone' />"+
		  "<param name='archive' value='"+  Configuration.ptmAppletPath +"' />"+
		  "<param name='ptmfile' value='"+ Configuration.url +"' />"+
//		  "<param name='ptmfile' value='" + hostAddress+ Configuration.url   +"' />"+
		  "<param name='bg_red' value='100' />"+
		  "<param name='bg_green' value='100' />"+
		  "<param name='bg_blue' value='100' />"+
		  "<param name='pw' value='"+ width +"' />"+
		  "<param name='ph' value='"+ height +"' />"+
		  "Applet failed to run.  No Java plugin was found."+
		"</object>"		  
  );

  
  
}(jQuery, Configuration));