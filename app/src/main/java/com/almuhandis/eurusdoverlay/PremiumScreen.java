package com.almuhandis.eurusdoverlay;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class PremiumScreen {
  public interface Actions { void changed(); void start(); void stop(); void battery(); void source(); }
  private static final int BG=0xff061121, PANEL=0xff10243a, DEEP=0xff08182b, EDGE=0xff254361;
  private static final int WHITE=0xfff6f9ff, SUB=0xff93a9c1, BLUE=0xff2675ff, MINT=0xff31e7ad;
  private static Activity a; private static Typeface regular,bold; private static SharedPreferences prefs;
  private static int dp(float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
  private static GradientDrawable bg(int color,int radius,int border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(border!=0)g.setStroke(dp(1),border);return g;}
  private static GradientDrawable grad(int c1,int c2,int radius,int border){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});g.setCornerRadius(dp(radius));if(border!=0)g.setStroke(dp(1),border);return g;}
  private static TextView txt(String s,int size,int color,boolean heavy){TextView v=new TextView(a);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setTypeface(heavy?bold:regular);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
  private static LinearLayout vertical(){LinearLayout l=new LinearLayout(a);l.setOrientation(1);return l;}
  private static LinearLayout horizontal(){LinearLayout l=new LinearLayout(a);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
  private static void gap(LinearLayout l,int h){View v=new View(a);l.addView(v,lp(1,h));}
  private static void loadFonts(){try{regular=Typeface.createFromAsset(a.getAssets(),"fonts/Cairo-Regular.ttf");bold=Typeface.createFromAsset(a.getAssets(),"fonts/Cairo-Bold.ttf");}catch(Exception e){regular=Typeface.create("sans-serif",Typeface.NORMAL);bold=Typeface.create("sans-serif",Typeface.BOLD);}}
  public static TextView render(Activity activity,Actions actions){
    a=activity; prefs=a.getSharedPreferences("overlay_prefs",Activity.MODE_PRIVATE);loadFonts();
    a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
    ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setVerticalScrollBarEnabled(false);
    LinearLayout root=vertical();root.setPadding(dp(18),dp(24),dp(18),dp(34));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(root);
    TextView title=txt("أسعار العملات",30,WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,lp(-1,48));
    TextView sub=txt("الشريط العائم",17,SUB,false);sub.setGravity(Gravity.CENTER);root.addView(sub,lp(-1,30));gap(root,14);
    TextView status=txt("",14,MINT,true);status.setGravity(Gravity.CENTER);status.setPadding(dp(22),dp(6),dp(22),dp(6));status.setBackground(bg(0xff0c443b,28,0xff126d5a));LinearLayout.LayoutParams st=lp(-2,40);st.gravity=Gravity.CENTER;root.addView(status,st);gap(root,23);
    LinearLayout hero=vertical();hero.setPadding(dp(18),dp(16),dp(18),dp(16));hero.setBackground(grad(0xff123d61,0xff061a31,25,BLUE));hero.setElevation(dp(9));root.addView(hero,lp(-1,-2));
    TextView heroLabel=txt("معاينة الشريط العائم",18,WHITE,true);hero.addView(heroLabel);gap(hero,4);
    TextView heroSub=txt("المظهر المصغّر فوق جميع التطبيقات",12,SUB,false);hero.addView(heroSub);gap(hero,16);
    LinearLayout sample=vertical();sample.setPadding(dp(13),dp(12),dp(13),dp(12));sample.setBackground(grad(0xff061426,0xff102b40,18,0xff2d5378));hero.addView(sample,lp(-1,-2));
    addSample(sample,"🇪🇺 🇺🇸","EUR/USD","1.1255",MINT);
    addSample(sample,"🇺🇸 🇪🇬","USD/EGP","53.4500",WHITE);
    addSample(sample,"🇺🇸 🇮🇱","USD/ILS","3.0886",WHITE);
    TextView demo=txt("قيم المعاينة توضيحية، والشريط الحقيقي يعرض الأسعار المباشرة",10,SUB,false);demo.setGravity(Gravity.CENTER);gap(hero,11);hero.addView(demo);
    gap(root,26);
    TextView heading=txt("الأزواج الظاهرة في الشاشة المنبثقة",19,WHITE,true);root.addView(heading);gap(root,5);
    TextView detail=txt("اختر العملات التي تريد عرضها فوق التطبيقات",12,SUB,false);root.addView(detail);gap(root,15);
    LinearLayout card=vertical();card.setPadding(dp(9),dp(10),dp(9),dp(10));card.setBackground(bg(PANEL,22,EDGE));root.addView(card,lp(-1,-2));
    pair(card,"🇪🇺 🇺🇸","EUR/USD","يورو / دولار","show_eurusd",true,actions);
    pair(card,"🇺🇸 🇪🇬","USD/EGP","دولار / جنيه مصري","show_usdegp",false,actions);
    pair(card,"🇪🇺 🇪🇬","EUR/EGP","يورو / جنيه مصري","show_euregp",false,actions);
    pair(card,"🇺🇸 🇮🇱","USD/ILS","دولار / شيكل","show_usdils",false,actions);
    pair(card,"🇺🇸 🇹🇷","USD/TRY","دولار / ليرة تركية","show_usdtry",false,actions);
    gap(root,20);
    action(root,"▶   تشغيل / تحديث الشريط العائم",grad(0xff138de8,0xff2041d7,17,0xff369eff),actions::start);
    gap(root,11);action(root,"■   إيقاف الشريط بالكامل",grad(0xff233b55,0xff16283d,17,EDGE),actions::stop);
    gap(root,18);action(root,"▣   إعدادات البطارية — اختر غير مقيّد",bg(PANEL,16,EDGE),actions::battery);
    gap(root,10);action(root,"↗   فتح EUR/USD في Investing.com",bg(PANEL,16,EDGE),actions::source);
    gap(root,18);TextView footer=txt("أي تغيير في الأزواج ينعكس مباشرة على الشريط أثناء تشغيله.\nاسحب الشريط بإصبعك لتغيير مكانه.",12,SUB,false);footer.setGravity(Gravity.CENTER);root.addView(footer,lp(-1,-2));
    a.setContentView(scroll);return status;
  }
  private static void addSample(LinearLayout target,String flags,String code,String price,int color){
    LinearLayout r=horizontal();r.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);target.addView(r,lp(-1,38));
    TextView f=txt(flags,16,WHITE,false);r.addView(f,lp(72,-1));
    TextView c=txt(code,15,WHITE,true);r.addView(c,new LinearLayout.LayoutParams(0,-1,1));
    TextView p=txt(price,18,color,true);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));r.addView(p);
  }
  private static void pair(LinearLayout target,String flags,String code,String label,String key,boolean def,Actions actions){
    LinearLayout r=horizontal();r.setPadding(dp(12),dp(7),dp(12),dp(7));r.setBackground(bg(DEEP,15,EDGE));LinearLayout.LayoutParams row=lp(-1,69);row.bottomMargin=dp(7);target.addView(r,row);
    Switch sw=new Switch(a);sw.setShowText(false);sw.setChecked(prefs.getBoolean(key,def));sw.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{WHITE,0xffa3b2c2}));sw.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,0xff536175}));r.addView(sw,lp(56,-2));
    LinearLayout name=vertical();name.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(0,-1,1);nl.setMargins(dp(6),0,dp(6),0);r.addView(name,nl);
    TextView codeView=txt(code,17,WHITE,true);codeView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);name.addView(codeView);
    TextView small=txt(label,11,SUB,false);small.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);name.addView(small);
    TextView flag=txt(flags,21,WHITE,false);flag.setGravity(Gravity.CENTER);r.addView(flag,lp(80,-1));
    r.setOnClickListener(v->sw.setChecked(!sw.isChecked()));
    sw.setOnCheckedChangeListener((b,checked)->{prefs.edit().putBoolean(key,checked).apply();actions.changed();});
  }
  private static void action(LinearLayout target,String label,GradientDrawable drawable,Runnable click){
    TextView b=txt(label,16,WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(drawable);b.setElevation(dp(2));target.addView(b,lp(-1,58));b.setOnClickListener(v->click.run());
  }
}
