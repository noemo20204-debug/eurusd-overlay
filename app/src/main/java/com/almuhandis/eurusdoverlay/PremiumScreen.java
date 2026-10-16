package com.almuhandis.eurusdoverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class PremiumScreen { // V4_REFINED
  public interface Actions { void changed(); void start(); void stop(); void battery(); void source(); }
  private static final int BG=0xff061121, PANEL=0xff0c1b2f, EDGE=0xff243952, WHITE=0xfff7faff, SUB=0xff9babc2, BLUE=0xff197cff, GREEN=0xff28d99b;
  private static Activity a; private static SharedPreferences prefs; private static Typeface regular,bold;
  private static int d(float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
  private static LinearLayout vertical(){LinearLayout l=new LinearLayout(a);l.setOrientation(1);return l;}
  private static LinearLayout horizontal(){LinearLayout l=new LinearLayout(a);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}
  private static void space(LinearLayout p,int h){p.addView(new View(a),lp(1,h));}
  private static GradientDrawable fill(int color,int radius,int border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));if(border!=0)g.setStroke(d(1),border);return g;}
  private static GradientDrawable gradient(int[] colors,int radius,int border){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);g.setCornerRadius(d(radius));if(border!=0)g.setStroke(d(1),border);return g;}
  private static TextView t(String value,int size,int color,boolean heavy){TextView v=new TextView(a);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setTypeface(heavy?bold:regular);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  private static void fonts(){try{regular=Typeface.createFromAsset(a.getAssets(),"fonts/Cairo-Regular.ttf");bold=Typeface.createFromAsset(a.getAssets(),"fonts/Cairo-Bold.ttf");}catch(Exception e){regular=Typeface.DEFAULT;bold=Typeface.DEFAULT_BOLD;}}
  public static TextView render(Activity activity,Actions actions){
    a=activity;prefs=a.getSharedPreferences("overlay_prefs",Activity.MODE_PRIVATE);fonts();
    a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);a.getWindow().getDecorView().setSystemUiVisibility(0);
    ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);scroll.setBackgroundColor(BG);
    LinearLayout root=vertical();root.setPadding(d(15),d(34),d(15),d(27));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(root);
    LinearLayout top=horizontal();root.addView(top,lp(-1,47));
    icon(top,"◎",()->actions.source());
    LinearLayout titles=vertical();titles.setGravity(Gravity.CENTER);top.addView(titles,new LinearLayout.LayoutParams(0,-1,1));
    TextView heading=t("أسعار العملات",27,WHITE,true);heading.setGravity(Gravity.CENTER);titles.addView(heading);
    icon(top,"⚙",()->actions.battery());
    TextView sub=t("الشريط العائم",17,SUB,false);sub.setGravity(Gravity.CENTER);root.addView(sub,lp(-1,31));
    space(root,9);
    TextView status=t("",13,GREEN,true);status.setGravity(Gravity.CENTER);status.setPadding(d(16),d(3),d(16),d(3));status.setBackground(fill(0xff063f34,30,0xff176a58));LinearLayout.LayoutParams st=lp(-2,35);st.gravity=Gravity.CENTER;root.addView(status,st);
    space(root,24);
    Hero hero=new Hero(a);root.addView(hero,lp(-1,162));
    space(root,24);
    LinearLayout section=horizontal();root.addView(section,lp(-1,50));
    TextView eye=t("◉",24,0xff40adff,true);eye.setGravity(Gravity.CENTER);eye.setBackground(fill(0xff10243f,13,EDGE));section.addView(eye,lp(43,43));
    LinearLayout sectionText=vertical();sectionText.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams sText=new LinearLayout.LayoutParams(0,-1,1);sText.setMargins(0,0,d(10),0);section.addView(sectionText,sText);
    TextView sectionTitle=t("معاينة الشريط العائم",17,WHITE,true);sectionText.addView(sectionTitle);
    TextView sectionSub=t("يظهر فوق جميع التطبيقات ويمكن سحبه لأي مكان",11,SUB,false);sectionText.addView(sectionSub);
    space(root,23);
    LinearLayout card=vertical();card.setPadding(d(13),d(18),d(13),d(15));card.setBackground(gradient(new int[]{0xff10223a,0xff08172b},21,EDGE));root.addView(card,lp(-1,-2));
    TextView pairTitle=t("الأزواج الظاهرة في الشاشة المنبثقة",18,WHITE,true);pairTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);card.addView(pairTitle,lp(-1,35));
    TextView pairSub=t("اختر الأزواج التي تريد ظهورها في الشريط العائم",11,SUB,false);pairSub.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);card.addView(pairSub,lp(-1,25));space(card,9);
    pair(card,"🇪🇺","🇺🇸","EUR/USD","يورو / دولار","show_eurusd",true,actions);
    pair(card,"🇺🇸","🇪🇬","USD/EGP","دولار / جنيه مصري","show_usdegp",false,actions);
    pair(card,"🇪🇺","🇪🇬","EUR/EGP","يورو / جنيه مصري","show_euregp",false,actions);
    pair(card,"🇺🇸","🇮🇱","USD/ILS","دولار / شيكل","show_usdils",false,actions);
    pair(card,"🇺🇸","🇹🇷","USD/TRY","دولار / ليرة تركية","show_usdtry",false,actions);
    space(card,9);
    action(card,"▶  تشغيل / تحديث الشريط العائم",gradient(new int[]{0xff159cff,0xff1744e2},16,0xff3aa9ff),58,actions::start);
    space(card,9);
    action(card,"■  إيقاف الشريط بالكامل",gradient(new int[]{0xff263e5b,0xff14263e},15,0xff39516d),55,actions::stop);
    space(card,13);
    action(card,"▣    إعدادات البطارية — اختر غير مقيّد     ›",fill(0xff0d2139,14,EDGE),55,actions::battery);
    space(card,9);
    action(card,"↗    فتح EUR/USD في Investing.com     ›",fill(0xff0d2139,14,EDGE),55,actions::source);
    space(root,14);
    TextView note=t("الأسعار داخل المعاينة توضيحية فقط. الأسعار الفعلية تظهر في الشريط العائم.",10,SUB,false);note.setGravity(Gravity.CENTER);root.addView(note,lp(-1,-2));
    a.setContentView(scroll);return status;
  }
  private static void icon(LinearLayout target,String symbol,Runnable run){TextView v=t(symbol,24,WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(fill(0xff10243c,14,EDGE));target.addView(v,lp(43,43));v.setOnClickListener(w->run.run());}
  private static void pair(LinearLayout target,String f1,String f2,String code,String label,String key,boolean def,Actions actions){
    // RTL visual order: drag handle, circular flag badges, names, toggle at right.
    LinearLayout row=horizontal();row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    row.setPadding(d(10),0,d(10),0);
    row.setBackground(gradient(new int[]{0xff07182c,0xff061326},16,0xff253b57));
    LinearLayout.LayoutParams rp=lp(-1,67);rp.bottomMargin=d(8);target.addView(row,rp);
    TextView drag=t("⠿",20,0xff7185a1,false);drag.setGravity(Gravity.CENTER);row.addView(drag,lp(26,-1));
    TextView flags=t(f1+" "+f2,22,WHITE,false);flags.setGravity(Gravity.CENTER);row.addView(flags,lp(91,-1));
    LinearLayout names=vertical();names.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(names,new LinearLayout.LayoutParams(0,-1,1));
    TextView main=t(code,16,WHITE,true);main.setTextDirection(View.TEXT_DIRECTION_LTR);names.addView(main);
    TextView minor=t(label,11,SUB,false);names.addView(minor);
    Toggle toggle=new Toggle(a,prefs.getBoolean(key,def));row.addView(toggle,lp(62,40));
    toggle.setOnClickListener(v->{toggle.enabled=!toggle.enabled;toggle.invalidate();prefs.edit().putBoolean(key,toggle.enabled).apply();actions.changed();});
    row.setOnClickListener(v->toggle.performClick());
  }
  private static void action(LinearLayout p,String label,GradientDrawable background,int height,Runnable onClick){TextView v=t(label,16,WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(background);v.setElevation(d(3));p.addView(v,lp(-1,height));v.setOnClickListener(w->onClick.run());}
  private static final class Toggle extends View {
    boolean enabled; Paint p=new Paint(3);
    Toggle(Activity a,boolean on){super(a);enabled=on;setClickable(true);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cy=h/2f,rad=Math.min(h*.40f,d(16));p.setColor(enabled?0xff1879ff:0xff3d4c60);p.setShadowLayer(enabled?d(7):0,0,0,0xff206dff);c.drawRoundRect(d(1),cy-rad,w-d(1),cy+rad,rad,rad,p);p.clearShadowLayer();p.setColor(enabled?Color.WHITE:0xffc2ccdb);float x=enabled?w-rad-d(4):rad+d(4);c.drawCircle(x,cy,rad*.79f,p);}
  }
  private static final class Hero extends View {
    Paint p=new Paint(3); Typeface digits=Typeface.create("sans-serif-condensed",Typeface.BOLD);
    Hero(Activity a){super(a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void text(Canvas c,String s,float x,float y,float size,int color,Typeface font){p.reset();p.setAntiAlias(true);p.setColor(color);p.setTypeface(font);p.setTextSize(d(size));c.drawText(s,x,y,p);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();float r=d(25);
      p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff102a4b,0xff030b1a,0xff071c34},null,Shader.TileMode.CLAMP));c.drawRoundRect(d(2),d(3),w-d(2),h-d(3),r,r,p);p.setShader(null);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d(1.3f));p.setColor(0xff227fff);c.drawRoundRect(d(2),d(3),w-d(2),h-d(3),r,r,p);p.setStyle(Paint.Style.FILL);
      p.setShader(new RadialGradient(w*.13f,h*.10f,w*.52f,new int[]{0x99338aff,0x002f75ff},null,Shader.TileMode.CLAMP));c.drawRoundRect(d(2),d(3),w-d(2),h-d(3),r,r,p);p.setShader(null);
      // Deliberately decorative dotted world-map silhouette; never interpreted as market data.
      p.setColor(0x354f9dce);float dot=d(1.15f);for(int ix=0;ix<65;ix++)for(int iy=0;iy<20;iy++){float xx=w*(ix+.5f)/65, yy=h*(iy+.5f)/20;float nx=xx/w,ny=yy/h;boolean land=(Math.pow((nx-.22)/.13,2)+Math.pow((ny-.38)/.24,2)<1)||(Math.pow((nx-.36)/.09,2)+Math.pow((ny-.76)/.22,2)<1)||(Math.pow((nx-.62)/.23,2)+Math.pow((ny-.42)/.22,2)<1)||(Math.pow((nx-.79)/.09,2)+Math.pow((ny-.76)/.11,2)<1);if(land)c.drawCircle(xx,yy,dot,p);}
      p.setColor(0xff72aefb);c.drawRoundRect(w*.45f,d(7),w*.55f,d(10),d(3),d(3),p);
      float left=d(20),base=d(49),step=d(42);String[] flags={"🇪🇺  🇺🇸","🇺🇸  🇪🇬","🇺🇸  🇮🇱"}, codes={"EUR/USD","USD/EGP","USD/ILS"},prices={"1.1255","53.4500","3.0886"};
      for(int i=0;i<3;i++){float y=base+step*i;text(c,flags[i],left,y,18,WHITE,regular);text(c,codes[i],w*.32f,y,15,WHITE,digits);text(c,prices[i],w*.69f,y,18,WHITE,digits);}
    }
  }
}
