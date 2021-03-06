package com.alogic.cert.xscript;

import org.apache.commons.lang3.StringUtils;

import com.alogic.cert.CertificateContent;
import com.alogic.xscript.ExecuteWatcher;
import com.alogic.xscript.Logiclet;
import com.alogic.xscript.LogicletContext;
import com.alogic.xscript.doc.XsObject;
import com.anysoft.util.Properties;
import com.anysoft.util.PropertiesConstants;

/**
 * 从证书内容中获取证书
 * @author yyduan
 * 
 * @since 1.6.11.9
 *
 */
public class GetCert extends CertificateOperation{

	protected String $id = "$cert-cert";

	public GetCert(String tag, Logiclet p) {
		super(tag, p);
	}

	@Override
	public void configure(Properties p){
		super.configure(p);
		
		$id = PropertiesConstants.getRaw(p,"id",$id);
	}
	
	@Override
	protected void onExecute(CertificateContent content, XsObject root,
			XsObject current, LogicletContext ctx, ExecuteWatcher watcher) {
		String id = PropertiesConstants.transform(ctx, $id, "$cert-key");
		if (StringUtils.isNotEmpty(id)){
			String key = new String(content.getCert());
			ctx.SetValue(id, key);
		}
	}

}
