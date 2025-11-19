# Testing Guide - Web Application

## Quick Start Testing

### 1. Start the Application

```bash
cd webapp
./start.sh
```

Wait for this message:
```
[INFO] Started Jetty Server
```

Then open: `http://localhost:8080`

## Testing Scenarios

### Scenario 1: First Time Load (No Reports)

**Expected Behavior:**
1. Page loads with all 20 tabs (A-T)
2. Each tab shows "No reports generated yet. Select groups and click Run Monitor."
3. RUN button is enabled immediately (no reports to load)

**Steps:**
1. Open `http://localhost:8080` in browser
2. Click through different tabs (A, B, C, etc.)
3. Verify each shows "No reports generated yet" message
4. Verify RUN button is enabled

### Scenario 2: Running Monitor for First Time

**Expected Behavior:**
1. Button disabled during execution
2. Shows "Running..." with spinner
3. After completion, reports appear in tabs
4. Button re-enabled only after reports load

**Steps:**
1. Select 2-3 groups (e.g., A, F, G) by checking their checkboxes
2. Verify selected tabs turn purple/highlighted
3. Choose mode (Actionable recommended for speed)
4. Click "RUN Monitor" button
5. Verify button changes to "Running..." with spinner
6. Try clicking button again (should be disabled)
7. Wait 1-3 minutes (depending on cluster size)
8. Watch for success message
9. Reports section updates in each tab
10. RUN button becomes enabled again

### Scenario 3: Subsequent Runs

**Expected Behavior:**
1. Reports from previous runs shown in tabs
2. RUN button briefly disabled while loading existing reports
3. Can run monitor again immediately after

**Steps:**
1. Refresh the page (`Cmd+R` or `F5`)
2. Watch RUN button - it should disable briefly
3. Reports load in each tab (latest 5)
4. RUN button enables after reports appear
5. Select different groups
6. Run monitor again
7. New reports added to the list

### Scenario 4: Report Display in Tabs

**Expected Behavior:**
1. Each tab shows latest 5 reports
2. Clicking "View" opens report in new tab
3. Report shows actual monitoring data

**Steps:**
1. After running monitor at least once
2. Click on tab "A" - should see reports
3. Click on tab "F" - should see reports
4. Click on any "View" button
5. New tab opens with HTML report
6. Report shows monitoring data in formatted HTML

### Scenario 5: Button State During Report Loading

**Expected Behavior:**
1. On page load with existing reports: button disabled
2. While loading reports: button disabled
3. After reports loaded: button enabled
4. During monitor run: button disabled
5. While refreshing reports after run: button disabled
6. After all done: button enabled

**Steps:**
1. Generate at least 1 report (run monitor once)
2. Refresh the page
3. Immediately check RUN button state (should be disabled)
4. Wait 1-2 seconds
5. Button should become enabled
6. This confirms proper state management

## Visual Verification Checklist

### Main Interface
- [ ] Header shows "OpenShift Intelligent Monitor"
- [ ] Two radio buttons for mode selection
- [ ] Three buttons: Select All, Deselect All, Run Monitor
- [ ] 20 tabs displayed in grid layout (A through T)
- [ ] Each tab shows: letter, name, command count

### Tab Content
- [ ] Clicking tab shows description
- [ ] Checkbox to include/exclude group
- [ ] Reports section at bottom of each tab
- [ ] Reports show: name, timestamp, size, View button

### Button States
- [ ] Enabled: Purple gradient, hover effect
- [ ] Disabled: Grayed out, no hover effect
- [ ] Running: Shows spinner, "Running..." text

### Reports Section
- [ ] Global reports at page bottom
- [ ] Each tab has its own reports section
- [ ] Reports sorted newest first
- [ ] Timestamps formatted properly
- [ ] File sizes human-readable (KB, MB)

## Common Issues & Solutions

### Issue: RUN button stays disabled
**Solution:** Check browser console (F12) for errors

### Issue: No reports appearing
**Solution:**
1. Ensure you're logged into OpenShift: `oc whoami`
2. Check `../reports/` directory exists
3. Verify script has execute permissions

### Issue: Script takes too long
**Solution:**
- Use Actionable mode instead of Verbose
- Select fewer groups for testing
- Check cluster responsiveness

### Issue: Page doesn't load
**Solution:**
```bash
# Check if port 8080 is in use
lsof -i :8080

# Use different port
mvn jetty:run -Djetty.http.port=9090
```

## Performance Testing

### Test with Different Group Combinations

1. **Single Group (Fast):**
   - Select only "A"
   - Should complete in ~30 seconds

2. **Essential Groups (Medium):**
   - Select A, F, G
   - Should complete in ~1-2 minutes

3. **All Groups (Slow):**
   - Click "Select All"
   - May take 5-10 minutes
   - Good for comprehensive reports

### Test Different Modes

1. **Actionable Mode:**
   - Faster execution
   - Smaller report files
   - Shows only problems

2. **Verbose Mode:**
   - Slower execution
   - Large report files (5-20 MB)
   - Complete information

## Browser Compatibility

Tested on:
- Chrome 120+
- Firefox 120+
- Safari 17+
- Edge 120+

Should work on any modern browser with JavaScript enabled.

## API Testing (Optional)

If you want to test the API directly:

### Get Categories
```bash
curl http://localhost:8080/api/categories
```

### Get Reports
```bash
curl http://localhost:8080/api/reports
```

### Run Monitor
```bash
curl -X POST http://localhost:8080/api/run-monitor \
  -H "Content-Type: application/json" \
  -d '{"groups":["A","F"],"mode":"actionable"}'
```

## Success Criteria

The application is working correctly if:

✓ All tabs load and display properly
✓ RUN button state changes appropriately
✓ Reports appear in tabs after execution
✓ Reports are viewable by clicking View button
✓ Can run multiple times without issues
✓ No JavaScript errors in browser console
✓ Button never stays disabled permanently
✓ Reports load within 2 seconds after generation

## Troubleshooting Commands

```bash
# Check if Java is running
ps aux | grep java

# Check Maven/Jetty logs
# (will be in terminal where you ran ./start.sh)

# Check script location
ls -la ../openshift_intelligent_monitor_v8.sh

# Check reports directory
ls -la ../reports/

# Test OpenShift connection
oc whoami
oc get nodes
```

## Next Steps After Testing

Once testing is complete:

1. Deploy to production Tomcat/WildFly server
2. Set up authentication if needed
3. Configure firewall rules
4. Set up automated monitoring schedules
5. Share URL with team members

For production deployment, see `README.md`
