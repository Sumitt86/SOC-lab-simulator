import { Routes, Route, Navigate } from 'react-router-dom';
import RoleSelect from './pages/RoleSelect';
import RedTeamConsole from './pages/RedTeamConsole';
import BlueTeamSOC from './pages/BlueTeamSOC';
import MissionControl from './pages/MissionControl';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RoleSelect />} />
      <Route path="/red" element={<RedTeamConsole />} />
      <Route path="/blue" element={<BlueTeamSOC />} />
      <Route path="/spectator" element={<MissionControl />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
