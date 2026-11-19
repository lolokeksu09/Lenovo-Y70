package com.moto.resolutionmaster;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AppListAdapter extends BaseAdapter {

    public interface OnAppToggleListener {
        void onToggle(AppInfo app, boolean enable);
    }

    private final Context context;
    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();
    private final OnAppToggleListener toggleListener;

    private String currentQuery = "";
    private int currentFilter = 0; // 0=All, 1=System, 2=User, 3=Disabled

    public AppListAdapter(Context context, List<AppInfo> apps, OnAppToggleListener toggleListener) {
        this.context = context;
        this.toggleListener = toggleListener;
        if (apps != null) {
            this.allApps.addAll(apps);
        }
        applyFilter();
    }

    public void updateList(List<AppInfo> newApps) {
        allApps.clear();
        if (newApps != null) {
            allApps.addAll(newApps);
        }
        applyFilter();
    }

    public void setQuery(String q) {
        this.currentQuery = (q == null) ? "" : q.trim().toLowerCase();
        applyFilter();
    }

    public void setFilter(int filterMode) {
        this.currentFilter = filterMode;
        applyFilter();
    }

    /** Reapply the active category and search after a package changes state. */
    public void refreshFilter() {
        applyFilter();
    }

    private void applyFilter() {
        filteredApps.clear();
        for (AppInfo a : allApps) {
            // Category check
            if (currentFilter == 1 && !a.isSystem) continue;
            if (currentFilter == 2 && a.isSystem) continue;
            if (currentFilter == 3 && !DisabledFilterPolicy.matches(a.enabledState)) continue;

            // Search query check (name, package, and description)
            if (!currentQuery.isEmpty()) {
                boolean matchName = a.label != null && a.label.toLowerCase().contains(currentQuery);
                boolean matchPkg = a.packageName != null && a.packageName.toLowerCase().contains(currentQuery);
                boolean matchDesc = a.description != null && a.description.toLowerCase().contains(currentQuery);
                if (!matchName && !matchPkg && !matchDesc) {
                    continue;
                }
            }

            filteredApps.add(a);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return filteredApps.size();
    }

    @Override
    public AppInfo getItem(int position) {
        return filteredApps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_app_icon);
            holder.tvName = convertView.findViewById(R.id.tv_app_name);
            holder.tvSafety = convertView.findViewById(R.id.tv_app_safety);
            holder.tvPkg = convertView.findViewById(R.id.tv_app_package);
            holder.tvDesc = convertView.findViewById(R.id.tv_app_desc);
            holder.tvType = convertView.findViewById(R.id.tv_app_type_badge);
            holder.tvStatus = convertView.findViewById(R.id.tv_app_status);
            holder.btnToggle = convertView.findViewById(R.id.btn_app_toggle);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final AppInfo app = getItem(position);

        holder.ivIcon.setImageDrawable(app.icon);
        holder.tvName.setText(app.label);
        holder.tvPkg.setText(app.packageName);
        holder.tvType.setText(app.isSystem ? "• Системное" : "• Стороннее");

        // Safety badge
        if (app.safetyLabel != null && !app.safetyLabel.isEmpty()) {
            holder.tvSafety.setVisibility(View.VISIBLE);
            holder.tvSafety.setText(app.safetyLabel);
            holder.tvSafety.setTextColor(app.safetyColor);
        } else {
            holder.tvSafety.setVisibility(View.GONE);
        }

        // Description
        if (app.description != null && !app.description.isEmpty()) {
            holder.tvDesc.setVisibility(View.VISIBLE);
            holder.tvDesc.setText(app.description);
        } else {
            holder.tvDesc.setVisibility(View.GONE);
        }

        boolean isProtected = PackageHelper.isProtectedInDebloater(context, app.packageName);
        holder.btnToggle.setEnabled(app.enabledState != null && !isProtected);
        if (app.enabledState == null) {
            holder.tvStatus.setText("● Состояние неизвестно");
            holder.tvStatus.setTextColor(0xFF9E9E9E);
            holder.btnToggle.setText("Недоступно");
            holder.btnToggle.setBackgroundResource(R.drawable.btn_chip);
        } else if (app.isEnabled) {
            holder.tvStatus.setText("● Активно");
            holder.tvStatus.setTextColor(0xFF10B981); // accent_green
            holder.btnToggle.setText("Отключить");
            holder.btnToggle.setBackgroundResource(R.drawable.btn_red);
        } else {
            holder.tvStatus.setText("● Отключено");
            holder.tvStatus.setTextColor(0xFFEF4444); // accent_red
            holder.btnToggle.setText("Включить");
            holder.btnToggle.setBackgroundResource(R.drawable.btn_green);
        }

        if (isProtected) {
            holder.btnToggle.setText("Защищено системой");
            holder.btnToggle.setBackgroundResource(R.drawable.btn_chip);
        }

        holder.btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleListener != null && app.enabledState != null &&
                        !PackageHelper.isProtectedInDebloater(context, app.packageName)) {
                    toggleListener.onToggle(app, !app.isEnabled);
                }
            }
        });

        return convertView;
    }

    static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
        TextView tvSafety;
        TextView tvPkg;
        TextView tvDesc;
        TextView tvType;
        TextView tvStatus;
        Button btnToggle;
    }
}
