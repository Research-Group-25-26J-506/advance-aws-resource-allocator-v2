import { useNavigate } from "react-router-dom";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import SpaceBetween from "@cloudscape-design/components/space-between";

export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <Box textAlign="center" padding="xxl">
      <SpaceBetween size="m">
        <Box fontSize="display-l" fontWeight="bold">
          404
        </Box>
        <Box variant="h2">Page not found</Box>
        <Box color="text-status-inactive">
          The page {window.location.pathname} doesn't exist, or you may not have access to it.
        </Box>
        <Box>
          <Button variant="primary" onClick={() => navigate("/")}>
            Back to Dashboard
          </Button>
        </Box>
      </SpaceBetween>
    </Box>
  );
}
